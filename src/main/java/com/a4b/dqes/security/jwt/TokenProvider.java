/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.security.jwt;

import static com.a4b.dqes.security.SecurityUtils.JWT_ALGORITHM;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.a4b.core.server.json.JSON;
import com.a4b.dqes.management.SecurityMetersService;
import com.dtsc.micservice.secmt.constants.CurrentUserInfoKey;
import com.dtsc.micservice.secmt.user.CurrentUserInfo;
import com.fasterxml.jackson.core.JsonProcessingException;

import tech.jhipster.config.JHipsterProperties;

@Component
public class TokenProvider {

    private static final String INVALID_JWT_TOKEN = "Invalid JWT token.";

    private final Logger log = LoggerFactory.getLogger(TokenProvider.class);

    private static final String AUTHORITIES_KEY = "auth";

    private long tokenValidityInMilliseconds;

    private long tokenValidityInMillisecondsForRememberMe;

    private final SecurityMetersService securityMetersService;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public TokenProvider(JHipsterProperties jHipsterProperties, SecurityMetersService securityMetersService, 
    JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
        this.tokenValidityInMilliseconds = 1000 * jHipsterProperties.getSecurity().getAuthentication().getJwt().getTokenValidityInSeconds();
        this.tokenValidityInMillisecondsForRememberMe =
            1000 * jHipsterProperties.getSecurity().getAuthentication().getJwt().getTokenValidityInSecondsForRememberMe();

        this.securityMetersService = securityMetersService;
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
    }

    public String createToken(Authentication authentication, boolean rememberMe) {
        String authorities = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(","));

        var now = Instant.now();
        Instant validity;
        if (rememberMe) {
            validity = now.plusMillis(this.tokenValidityInMillisecondsForRememberMe);
        } else {
            validity = now.plusMillis(this.tokenValidityInMilliseconds);
        }

        JwtClaimsSet claims = JwtClaimsSet
            .builder()
            .issuedAt(now)
            .expiresAt(validity)
            .subject(authentication.getName())
            .claims(customClain -> customClain.put(AUTHORITIES_KEY, authorities))
            .build();

        JwsHeader jwsHeader = JwsHeader.with(JWT_ALGORITHM).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }

    public String createTokenWithRole(String authenticationName, long expiredTime,  CurrentUserInfo userInfo) {
        String authorities = userInfo.getAuthorities() != null && !userInfo.getAuthorities().isEmpty()
                ? userInfo.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.joining(","))
                : null;

        var now = Instant.now();
        Instant validity = now.plusMillis(expiredTime);

        JwtClaimsSet claims = JwtClaimsSet
            .builder()
            .issuedAt(now)
            .expiresAt(validity)
            .subject(authenticationName)
            .claims(customClain -> {
                customClain.put(AUTHORITIES_KEY, authorities);
                customClain.put(CurrentUserInfoKey.USERINFO_KEY, JSON.getGson().toJson(userInfo));
            })
            .build();

        JwsHeader jwsHeader = JwsHeader.with(JWT_ALGORITHM).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }

    public Authentication getAuthentication(String token) {
        Jwt jwt = jwtDecoder.decode(token);

        Collection<? extends GrantedAuthority> authorities = Arrays
                .stream(jwt.getClaim(CurrentUserInfoKey.AUTHORITIES_KEY).toString().split(","))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        List<String> roles = Arrays.asList(jwt.getClaim(CurrentUserInfoKey.AUTHORITIES_KEY).toString().split(","));

        CurrentUserInfo userInfo = JSON.getGson().fromJson(
                    (String) jwt.getClaim(CurrentUserInfoKey.USERINFO_KEY),
                    CurrentUserInfo.class);

        if (userInfo != null) {
            if (userInfo.getMoreInfo() == null) {
                userInfo.setMoreInfo(new HashMap<>());
            }
            userInfo.getMoreInfo().put(CurrentUserInfoKey.AUTHORITIES_KEY, roles);
        }
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                jwt.getSubject(), token, authorities);
        authenticationToken.setDetails(userInfo);
        return authenticationToken;
    }

    public boolean validateToken(String token) {
        try {
            jwtDecoder.decode(token);
            return true;
        } catch (Exception e) {
            if (e.getMessage().contains("Invalid signature")) {
                securityMetersService.trackTokenInvalidSignature();
            } else if (e.getMessage().contains("Jwt expired at")) {
                securityMetersService.trackTokenExpired();
            } else if (e.getMessage().contains("Invalid JWT serialization")) {
                securityMetersService.trackTokenMalformed();
            } else if (e.getMessage().contains("Invalid unsecured/JWS/JWE")) {
                securityMetersService.trackTokenMalformed();
            }
        }

        return false;
    }

    public Jwt parseToken(String token) {
    	return jwtDecoder.decode(token);
    }
}
