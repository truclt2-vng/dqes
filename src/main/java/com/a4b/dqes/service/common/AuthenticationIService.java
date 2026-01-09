/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.service.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.a4b.dqes.security.jwt.TokenProvider;

import reactor.core.publisher.Mono;


@Service
public class AuthenticationIService {
	private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationIService.class);

	private final Map<String, String> authorizedClients;
	private TokenProvider tokenProvider;

	public AuthenticationIService(TokenProvider tokenProvider) {
		this.authorizedClients = new ConcurrentHashMap<>();
		this.tokenProvider = tokenProvider;
	}

	public String loadToken(String username) {
		return authorizedClients.get(username);
	}

	public Mono<String> loadToken(Mono<String> username) {
		return username.map(un -> authorizedClients.get(un));
	}

	public void saveToken(String username, String token) {
		authorizedClients.put(username, token);
	}

	public boolean validateToken(String token) {
		try {
			Jwt jwt = tokenProvider.parseToken(token);
			return !isExpired(jwt);
		} catch (Exception e) {
			LOGGER.trace("Invalid JWT token", e);
		}
		return false;
	}

	private boolean isExpired(Jwt jwt) {
		if (jwt.getExpiresAt() == null) {
			return false;
		}

		Instant now = Instant.now();
		Instant expiresAt = jwt.getExpiresAt();
		return now.isAfter(expiresAt.minus(Duration.ofMinutes(1L)));
	}
}
