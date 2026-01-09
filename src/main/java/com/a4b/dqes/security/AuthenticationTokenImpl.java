/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class AuthenticationTokenImpl extends UsernamePasswordAuthenticationToken implements AuthenticationToken {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5622624944815564410L;

	public AuthenticationTokenImpl(Object principal, Object credentials) {
		super(principal, credentials);
	}

	public AuthenticationTokenImpl(Object principal, Object credentials,
			Collection<? extends GrantedAuthority> authorities) {
		super(principal, credentials, authorities);
	}

	@Override
	public UserInfo getUserInfo() {
		return ((CurrentUserDetails)getDetails()).getUserInfo();
	}

	@Override
	public String[] getRoles() {
		List<String> roles = new ArrayList<String>();
		for (GrantedAuthority role : getAuthorities()) {
			roles.add(role.getAuthority());
		}
		return (String[])roles.toArray(new String[roles.size()]);
	}
	
}
