/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.security;


public class AuthenticationContext {

	private static final ThreadLocal<String> tlAppInfo = new ThreadLocal<>();
	private static final ThreadLocal<String> tlJwt = new ThreadLocal<>();
	
	public static final void setAppCode(String appCode) {
		tlAppInfo.set(appCode);
	}
	
	public static String getAppCode() {
		return tlAppInfo.get();
	}
	
	public static String getJwt() {
		return tlJwt.get();
	}
	
	public static final void setJwt(String jwt) {
		tlJwt.set(jwt);
	}
	
	public static final void clear() {
		tlJwt.remove();
		tlAppInfo.remove();
	}
}
