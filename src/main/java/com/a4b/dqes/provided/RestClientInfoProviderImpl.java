/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.provided;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;

import com.a4b.core.rest.prov.RestClientInfoProvider;
import com.a4b.dqes.security.AuthenticationContext;
import com.a4b.dqes.security.jwt.JWTFilter;


@Component
public class RestClientInfoProviderImpl implements RestClientInfoProvider {

	@Value("${field-sec.url}")
	private String secmtServiceUrl;

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private WebClient.Builder webClientBuilder;
	@Autowired
	private WebClient externalWebClient;

	@Override
	public String getEzmtServiceUrl() {
		return secmtServiceUrl;
	}

	@Override
	public String getJwt() {
		return AuthenticationContext.getJwt();
	}

	@Override
	public String getAuthorizationHeaderName() {
		return JWTFilter.AUTHORIZATION_HEADER;
	}

	@Override
	public RestTemplate getRestTemplate() {
		return restTemplate;
	}

	@Override
	public Builder getWebClientBuilder() {
		return webClientBuilder;
	}

	@Override
	public Builder getExternalWebClientBuilder() {
		return webClientBuilder;
	}

	public WebClient getExternalWebClient() {
		return externalWebClient;
	}

	public void setExternalWebClient(WebClient externalWebClient) {
		this.externalWebClient = externalWebClient;
	}
}
