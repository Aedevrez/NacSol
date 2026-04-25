# Project Context – FreeRADIUS as RADIUS Gateway, Spring as AAA Policy Manager

## Core Objective (VERY IMPORTANT)

FreeRADIUS is used ONLY to:
- Receive RADIUS packets from NAS devices
- Translate them into HTTP requests using rlm_rest
- Forward the full request context to the Spring backend
- Apply the decision returned by Spring (accept/reject + attributes)

ALL AAA logic is implemented in the Spring backend.

FreeRADIUS must NOT:
- Validate passwords or compare secrets
- Apply authorization rules
- Contain business or policy logic of any kind
- Make accept/reject decisions on its own

---

## High-Level Architecture

```
NAS → FreeRADIUS (RADIUS/UDP 1812/1813) → Spring Backend (HTTPS/JSON)
```

- FreeRADIUS = thin protocol gateway only
- Spring Boot application = centralized AAA Policy Manager (single source of truth)

---

## AAA Responsibility Split

### Authentication
- Performed exclusively by Spring
- Spring validates credentials (password, OTP, external IdP, etc.)
- Spring returns an explicit HTTP decision:
    - `200` → Access-Accept (optionally with reply attributes)
    - `401` → Access-Reject (wrong credentials)
    - `403` → Access-Reject (account locked / disabled)

FreeRADIUS does NOT compare passwords or inspect credentials.

### Authorization
- Performed exclusively by Spring
- Spring evaluates:
    - User identity and role
    - Device identity (MAC, device type)
    - Location / NAS context (which AP, which building)
    - Time and contextual policies
    - Internal company policies

Authorization output is returned as RADIUS attributes (standard + VSA) in the response body.

### Accounting
- FreeRADIUS forwards all Accounting-Request packets to Spring
- Spring is the system of record for session lifecycle and usage data
- FreeRADIUS does not store any accounting state

---

## Authentication Model

- PAP at the RADIUS layer — acceptable; password is forwarded to Spring in plaintext over HTTPS
- EAP, CHAP, MS-CHAP — out of scope unless explicitly requested
- HTTPS is MANDATORY for all FreeRADIUS → Spring communication
- FreeRADIUS uses `Auth-Type := rest` unconditionally — authentication is always delegated to Spring

---

## FreeRADIUS Design Rules

- `rlm_rest` is the ONLY decision-making module
- FreeRADIUS configs must be thin and declarative
- **No conditional policy logic** — no `if/else` blocks in unlang based on attribute values or business rules
- Static `update` blocks for wiring (e.g. setting `Auth-Type`, injecting service headers) are permitted
- No policy logic in: SQL, files, ldap, or any other module
- FreeRADIUS reacts only to the HTTP status code and attributes returned by Spring

---

## FreeRADIUS Pipeline & Virtual Server Wiring

FreeRADIUS processes Access-Request packets through a pipeline of named sections. Each section
maps to one rlm_rest call. The virtual server (`sites-enabled/default`) must be wired as follows:

```
authorize   → POST /radius/authorize    (user lookup + attribute loading)
authenticate → POST /radius/authenticate (credential validation, Auth-Type rest)
post-auth   → POST /radius/post-auth    (fires on BOTH accept and reject — for audit logging)
accounting  → POST /radius/accounting   (session lifecycle: Start / Interim-Update / Stop)
```

`preacct` is **out of scope** for this project.
`pre-proxy` / `post-proxy` are **out of scope** — no RADIUS proxying is used.

### Virtual server skeleton (sites-enabled/default)

```
server default {
    authorize {
        update control {
            Auth-Type := rest    # static wiring — always delegate auth to Spring
        }
        rest                     # calls POST /radius/authorize
    }

    authenticate {
        Auth-Type rest {
            rest                 # calls POST /radius/authenticate
        }
    }

    post-auth {
        rest                     # calls POST /radius/post-auth (on accept)
        Post-Auth-Type REJECT {
            rest                 # calls POST /radius/post-auth (on reject too — for audit)
        }
    }

    accounting {
        rest                     # calls POST /radius/accounting
    }
}
```

---

## REST Interface Contract

### General
- All REST communication uses JSON (`Content-Type: application/json`)
- Spring controllers map 1:1 to FreeRADIUS pipeline stages
- HTTPS is required; FreeRADIUS TLS config must reference the CA bundle

### Required Spring Endpoints

| Method | Path                   | Pipeline stage | Purpose                                      |
|--------|------------------------|----------------|----------------------------------------------|
| POST   | /radius/authorize      | authorize      | User/device lookup; load policy attributes   |
| POST   | /radius/authenticate   | authenticate   | Credential validation; returns accept/reject |
| POST   | /radius/post-auth      | post-auth      | Audit log for both accept and reject events  |
| POST   | /radius/accounting     | accounting     | Session lifecycle (Start/Interim/Stop)       |

### Decision Semantics (HTTP status → RADIUS outcome)

| HTTP Status    | rlm_rest code    | Body read? | Outcome                                                     |
|----------------|------------------|------------|-------------------------------------------------------------|
| 200 OK         | ok / updated     | Yes        | Success. Attributes in body are applied to the packet.      |
| 204 No Content | ok               | No         | Success, no attributes. Preferred response for accounting.  |
| 401            | reject           | No         | Wrong credentials. Sends Access-Reject to the NAS.          |
| 403            | userlock         | No         | Account locked/disabled. Sends Access-Reject.               |
| 404            | notfound         | No         | User not found. In this architecture: treated as reject.    |
| 5xx            | fail             | No         | Backend error. FreeRADIUS sends Access-Reject (fail-safe).  |

> **Note on 404:** Because `rlm_rest` is the only module, `notfound` has no fallthrough target.
> Spring should return `404` only for genuinely unknown users; FreeRADIUS will reject them.

---

## Request Payload Construction

### Philosophy
FreeRADIUS must send **clean, domain-level data** to Spring. Spring DTOs must never
contain raw RADIUS attribute names or protocol-level internals.

### Mechanism: `data` field with xlat expressions
The `data` config item in each rlm_rest section defines a custom JSON template.
xlat expressions (`%{Attribute-Name}`) are expanded at request time from the current RADIUS packet.
This completely replaces the default RADIUS attribute serialization.

```
# In mods-enabled/rest — authorize section:
authorize {
    uri    = "${..connect_uri}/radius/authorize"
    method = 'post'
    body   = 'json'       # sets Content-Type: application/json
    data   = '{
        "username":    "%{User-Name}",
        "password":    "%{User-Password}",
        "nasIp":       "%{NAS-IP-Address}",
        "nasId":       "%{NAS-Identifier}",
        "deviceMac":   "%{Calling-Station-Id}",
        "ssid":        "%{urlquote:%{Called-Station-Id}}",
        "serviceType": "%{Service-Type}",
        "sessionId":   "%{Acct-Unique-Session-ID}"
    }'
}
```

### Key xlat expressions (RADIUS attribute → JSON field mapping)

| JSON field     | xlat expression                         | Notes                                          |
|----------------|-----------------------------------------|------------------------------------------------|
| username       | `%{User-Name}`                          | Authenticating identity                        |
| password       | `%{User-Password}`                      | PAP only — forward to Spring over HTTPS        |
| nasIp          | `%{NAS-IP-Address}`                     | IP of the network device                       |
| nasId          | `%{NAS-Identifier}`                     | Hostname/name of the NAS if set                |
| deviceMac      | `%{Calling-Station-Id}`                 | MAC address of the connecting device           |
| ssid           | `%{urlquote:%{Called-Station-Id}}`      | AP MAC + SSID string; URL-encode it            |
| serviceType    | `%{Service-Type}`                       | Type of access being requested                 |
| sessionId      | `%{Acct-Unique-Session-ID}`             | FreeRADIUS-computed unique session hash        |
| realm          | `%{Realm}`                              | Part of User-Name after `@`, if present        |

> **Always use `%{urlquote:...}`** when embedding string attributes in URIs — values like
> `AA:BB:CC:DD:EE:FF:CorpSSID` will break URLs without encoding.

---

## Inter-Service Authentication (FreeRADIUS → Spring)

FreeRADIUS must authenticate itself to Spring on every request.
Use a static API key or Bearer token injected via custom headers:

```
# In mods-enabled/rest — global or per-section:
authorize {
    header = "X-API-Key: <shared-secret>"
    header = "X-Source: freeradius"
    # FreeRADIUS also sends X-FreeRADIUS-Server: <virtual-server-name> automatically
}
```

Alternatively use HTTP Basic Auth at the transport level:
```
authorize {
    auth     = basic
    username = "freeradius-svc"
    password = "<service-account-password>"
}
```

Spring must validate this header/credential on every request from FreeRADIUS.

---

## Policy Output Model

Spring response bodies follow the rlm_rest JSON attribute format.
Attributes are prefixed with their target list (`reply:`, `control:`).

```json
{
  "reply:Session-Timeout":              { "value": [28800],     "op": ":=" },
  "reply:Tunnel-Type":                  { "value": [13],        "op": ":=" },
  "reply:Tunnel-Medium-Type":           { "value": [6],         "op": ":=" },
  "reply:Tunnel-Private-Group-Id":      { "value": ["100"],     "op": ":=" },
  "reply:MyCompany-AccessTier":         { "value": ["Employee"],"op": ":=" },
  "reply:MyCompany-BandwidthLimit":     { "value": [10240],     "op": ":=" }
}
```

- `reply:` attributes are sent to the NAS in the Access-Accept packet
- `control:` attributes affect FreeRADIUS internal behaviour only (not sent to NAS)
- Accounting responses should return `204 No Content` (no body needed)

---

## Vendor-Specific Attributes (VSAs)

VSAs are the primary policy enforcement mechanism. They carry company-specific
attributes beyond what standard RADIUS supports.

### Dictionary file

Location: `/etc/freeradius/3.0/dictionary.mycompany`

```
VENDOR          MyCompany        99999      # replace 99999 with your IANA PEN

BEGIN-VENDOR    MyCompany

ATTRIBUTE       MyCompany-Department        1    string
ATTRIBUTE       MyCompany-Role              2    string
ATTRIBUTE       MyCompany-AccessTier        3    integer
ATTRIBUTE       MyCompany-BandwidthLimit    4    integer
ATTRIBUTE       MyCompany-DeviceType        5    string
ATTRIBUTE       MyCompany-CostCenter        6    string

VALUE           MyCompany-AccessTier        Guest        0
VALUE           MyCompany-AccessTier        Employee     1
VALUE           MyCompany-AccessTier        Admin        2
VALUE           MyCompany-AccessTier        Contractor   3

END-VENDOR      MyCompany
```

Include in `/etc/freeradius/3.0/dictionary`:
```
$INCLUDE dictionary.mycompany
```

### VSA lifecycle
- **Authorize:** Spring returns VSAs in the response body → FreeRADIUS sets them on the `reply:` list → sent to NAS in Access-Accept
- **Caching (optional):** VSAs set during authorize can be cached by `rlm_cache` and replayed into the accounting body — enriching accounting packets with context that was never in the original Accounting-Request
- **Accounting:** Replayed VSAs arrive at Spring's accounting endpoint as part of the session context

NAS devices that don't understand a VSA will silently ignore it — this is acceptable and expected.

---

## Accounting Model

### Session correlation
Use `Acct-Unique-Session-ID` (not `Acct-Session-Id`) as the primary session key.
`Acct-Unique-Session-ID` is a hash computed by FreeRADIUS from the NAS IP, NAS port, and session
data — it is globally unique across all NAS devices. `Acct-Session-Id` is assigned by the NAS
and is only unique within a single NAS; it can collide across devices.

### Event types (Acct-Status-Type)

| Value | Name           | Spring action                                                |
|-------|----------------|--------------------------------------------------------------|
| 1     | Start          | Create session record; store sessionId, user, IP, NAS       |
| 3     | Interim-Update | Update running byte counters; confirm session is alive       |
| 2     | Stop           | Close session; record duration, total bytes, terminate cause |
| 7     | Accounting-On  | NAS came online; mark stale sessions from this NAS as ended |
| 8     | Accounting-Off | NAS going offline; close all its active sessions             |

### Accounting payload fields

```
accounting {
    uri    = "${..connect_uri}/radius/accounting"
    method = 'post'
    body   = 'json'
    data   = '{
        "sessionId":       "%{Acct-Unique-Session-ID}",
        "nasSessionId":    "%{Acct-Session-Id}",
        "statusType":      %{Acct-Status-Type},
        "username":        "%{User-Name}",
        "nasIp":           "%{NAS-IP-Address}",
        "framedIp":        "%{Framed-IP-Address}",
        "deviceMac":       "%{Calling-Station-Id}",
        "ssid":            "%{urlquote:%{Called-Station-Id}}",
        "sessionTime":     %{Acct-Session-Time},
        "bytesIn":         %{Acct-Input-Octets},
        "bytesOut":        %{Acct-Output-Octets},
        "terminateCause":  "%{Acct-Terminate-Cause}"
    }'
}
```

All accounting endpoints must be **idempotent** — Interim-Updates may arrive multiple
times for the same session due to NAS retransmission.

---

## Spring Backend Expectations

- Spring is a **policy engine**, not a RADIUS server
- No RADIUS-specific assumptions in any Spring class
- Use explicit request/response DTOs mapped from the JSON payload above
- Stateless REST endpoints (all session state is in the database, not in-memory)
- Spring must validate the inter-service credential (API key / Basic Auth) on every request
- The `/radius/post-auth` endpoint must handle both accept and reject events —
  distinguish them via a field in the payload (e.g. `"authResult": "accept"` / `"reject"`)

---

## Connection Pool (rlm_rest)

FreeRADIUS maintains a persistent HTTP connection pool to Spring. Recommended settings:

```
pool {
    start        = 5
    min          = 4
    max          = 10
    spare        = 3
    uses         = 0
    retry_delay  = 30
    lifetime     = 0
    idle_timeout = 60
}
```

Tune `max` based on expected concurrent authentication load. Each pool slot is one
persistent HTTPS connection.

---

## What Must NEVER Be Done

- Do not validate or compare passwords in FreeRADIUS
- Do not write conditional authorization logic in unlang (`if/else` based on attribute values)
- Do not use FreeRADIUS SQL, files, ldap, or any other module for policy decisions
- Do not return `control:Cleartext-Password` from Spring — this would cause FreeRADIUS to check
  the password itself, violating the "no local auth" rule
- Do not duplicate policy between FreeRADIUS and Spring
- Do not use `Auth-Type := Accept` — this would bypass Spring's authenticate step entirely

---

## Output Expectations for Claude Code

When generating code or configuration:
- Assume FreeRADIUS 3.2.8
- Assume Spring Boot 3.x (Jakarta EE, not javax)
- Use the `data` + xlat approach for all request payloads — never rely on raw RADIUS JSON serialization
- Favor clarity over cleverness
- Add a comment explaining why each config block exists
- Treat Spring as the single source of truth for all AAA decisions
- All FreeRADIUS → Spring calls must use HTTPS
- Spring DTOs must not expose RADIUS attribute names
