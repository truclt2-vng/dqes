/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth", ignoreUnknownFields = false)
public class AuthProperties {
	private Map<String, String> onBehalfJwt = new HashMap<>();
	private Map<String, String> onBehalfAuth = new HashMap<>();

	public Map<String, String> getOnBehalfJwt() {
		return onBehalfJwt;
	}

	public void setOnBehalfJwt(Map<String, String> onBehalfJwt) {
		this.onBehalfJwt = onBehalfJwt;
	}

	public Map<String, String> getOnBehalfAuth() {
		return onBehalfAuth;
	}

	public void setOnBehalfAuth(Map<String, String> onBehalfAuth) {
		this.onBehalfAuth = onBehalfAuth;
	}

}
