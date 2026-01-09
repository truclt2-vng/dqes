/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.constant;

/**
 * Application constants.
 */
public final class Constants {

    public static final String SYSTEM = "system";

    // Regex for acceptable logins
    public static final String LOGIN_REGEX = "^[_'.@A-Za-z0-9-]*$";

    public static final String SYSTEM_ACCOUNT = "system";
    public static final String ANONYMOUS_USER = "anonymoususer";
    public static final String DEFAULT_LANGUAGE = "en";
    
    public static final String EXTRA_INFO_FILTER_PREFIX = "extraInfo.";
    public static final String EXTRA_INFO_PROP = "extraInfo";
    
    public static final String REMOTE_USER_MCR = "mcr";
    
    public static final String PROP_FTS_VALUE = "ftsValue";
    public static final String APP_CODE = "APP_CODE";
    public static final String TENANT_CODE = "A4B";
	
	public static final String PROPERTIES_FILTER_PREFIX = "properties.";
	public static final String PROPERTIES_PROP = "properties";
	
	public static final String CONTEXT_JWT = "jwt";
	public static final String CONTEXT_RUN_AS_JWT = "runAsJwt";
    
    private Constants() {
    }
}
