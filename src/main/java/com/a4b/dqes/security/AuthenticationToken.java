/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.security;

import org.springframework.security.core.Authentication;

public interface AuthenticationToken extends Authentication {
	public UserInfo getUserInfo();

	public String[] getRoles();
}
