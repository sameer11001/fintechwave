package com.fintechwave.security.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            authorities.addAll(roles.stream()
                    .filter(role -> !role.equals("offline_access")
                            && !role.equals("uma_authorization")
                            && !role.startsWith("default-roles"))
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toList()));
        }

        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            resourceAccess.forEach((clientName, clientInfo) -> {
                if (clientInfo instanceof Map) {
                    Map<String, Object> clientMap = (Map<String, Object>) clientInfo;
                    if (clientMap.containsKey("roles")) {
                        @SuppressWarnings("unchecked")
                        List<String> clientRoles = (List<String>) clientMap.get("roles");
                        authorities.addAll(clientRoles.stream()
                                .map(role -> new SimpleGrantedAuthority(
                                        "ROLE_" + clientName.toUpperCase() + "_" + role.toUpperCase()))
                                .collect(Collectors.toList()));
                    }
                }
            });
        }
        return new JwtAuthenticationToken(jwt, authorities, getPrincipalClaimName(jwt));
    }

    private String getPrincipalClaimName(Jwt jwt) {
        String claimName = "preferred_username";
        return jwt.hasClaim(claimName) ? jwt.getClaimAsString(claimName) : jwt.getSubject();
    }
}