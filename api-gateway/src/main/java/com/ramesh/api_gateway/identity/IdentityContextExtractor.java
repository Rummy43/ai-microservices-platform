package com.ramesh.api_gateway.identity;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Builds an {@link IdentityContext} from an already-validated Keycloak {@link Jwt}.
 *
 * <p>This does NOT validate the token — authentication is owned by the OAuth2
 * Resource Server. It only reads claims that Spring Security has already verified,
 * mirroring the realm-role extraction used for authority mapping but for the
 * separate concern of downstream identity propagation.
 */
@Component
public class IdentityContextExtractor {

    private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_REALM_ACCESS = "realm_access";
    private static final String CLAIM_ROLES = "roles";

    public IdentityContext extract(Jwt jwt) {
        String username = jwt.getClaimAsString(CLAIM_PREFERRED_USERNAME);
        String email = jwt.getClaimAsString(CLAIM_EMAIL);
        List<String> roles = extractRealmRoles(jwt);

        return new IdentityContext(username, email, roles);
    }

    private List<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(CLAIM_REALM_ACCESS);

        if (realmAccess == null) {
            return List.of();
        }

        Object roles = realmAccess.get(CLAIM_ROLES);

        if (roles instanceof Collection<?> roleCollection) {
            return roleCollection.stream()
                    .map(String::valueOf)
                    .toList();
        }

        return List.of();
    }
}