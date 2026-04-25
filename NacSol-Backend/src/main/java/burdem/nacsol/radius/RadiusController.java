package burdem.nacsol.radius;

import burdem.nacsol.radius.dto.RadiusAccessRequest;
import burdem.nacsol.radius.dto.RadiusAccountingRequest;
import burdem.nacsol.radius.dto.RadiusPostAuthRequest;
import burdem.nacsol.radius.service.AccountingService;
import burdem.nacsol.radius.service.AuthenticateService;
import burdem.nacsol.radius.service.AuthorizeService;
import burdem.nacsol.radius.service.PostAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/radius")
public class RadiusController {

    private final AuthorizeService authorizeService;
    private final AuthenticateService authenticateService;
    private final PostAuthService postAuthService;
    private final AccountingService accountingService;

    public RadiusController(AuthorizeService authorizeService,
                            AuthenticateService authenticateService,
                            PostAuthService postAuthService,
                            AccountingService accountingService) {
        this.authorizeService = authorizeService;
        this.authenticateService = authenticateService;
        this.postAuthService = postAuthService;
        this.accountingService = accountingService;
    }

    /** User/device lookup — returns policy attributes (VLAN, VSAs) in rlm_rest format */
    @PostMapping("/authorize")
    public ResponseEntity<Map<String, Object>> authorize(@RequestBody RadiusAccessRequest request) {
        Map<String, Object> attributes = authorizeService.authorize(request);
        return ResponseEntity.ok(attributes);
    }

    /** Credential validation — 200 = Access-Accept, exceptions map to 401/403/404 */
    @PostMapping("/authenticate")
    public ResponseEntity<Void> authenticate(@RequestBody RadiusAccessRequest request) {
        authenticateService.authenticate(request);
        return ResponseEntity.ok().build();
    }

    /** Audit log for both accept and reject events */
    @PostMapping("/post-auth")
    public ResponseEntity<Void> postAuth(@RequestBody RadiusPostAuthRequest request) {
        postAuthService.logAuthEvent(request);
        return ResponseEntity.noContent().build();
    }

    /** Session lifecycle: Start / Interim-Update / Stop / Accounting-On / Accounting-Off */
    @PostMapping("/accounting")
    public ResponseEntity<Void> accounting(@RequestBody RadiusAccountingRequest request) {
        accountingService.processAccounting(request);
        return ResponseEntity.noContent().build();
    }
}
