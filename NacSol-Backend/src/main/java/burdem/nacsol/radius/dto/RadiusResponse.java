package burdem.nacsol.radius.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for the rlm_rest JSON attribute format.
 * Produces a map like: {"reply:Session-Timeout": {"value": [28800], "op": ":="}}
 */
public class RadiusResponse {

    private final Map<String, Object> attributes = new LinkedHashMap<>();

    public static RadiusResponse create() {
        return new RadiusResponse();
    }

    public RadiusResponse reply(String attribute, Object value) {
        attributes.put("reply:" + attribute, Map.of(
                "value", List.of(value),
                "op", ":="
        ));
        return this;
    }

    public RadiusResponse control(String attribute, Object value) {
        attributes.put("control:" + attribute, Map.of(
                "value", List.of(value),
                "op", ":="
        ));
        return this;
    }

    public Map<String, Object> build() {
        return Collections.unmodifiableMap(attributes);
    }
}
