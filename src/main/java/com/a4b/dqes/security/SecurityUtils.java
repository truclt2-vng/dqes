/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import com.a4b.core.server.ApplicationContextStore;
import com.a4b.core.server.utils.RunAsWork;
import com.a4b.dqes.config.AuthProperties;
import com.a4b.dqes.constant.Constants;
import com.a4b.dqes.exception.IntegrationRuntimeException;
import com.a4b.dqes.security.jwt.TokenProvider;
import com.a4b.dqes.service.common.AuthenticationIService;
import com.dtsc.micservice.secmt.constants.CurrentUserInfoKey;
import com.dtsc.micservice.secmt.user.CurrentUserInfo;

/**
 * Utility class for Spring Security.
 */
public final class SecurityUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(SecurityUtils.class);

    public static final MacAlgorithm JWT_ALGORITHM = MacAlgorithm.HS512;

    public static final String AUTHORITIES_KEY = "auth";
    public static final String APPDATA_KEY = "appData";
	
    private SecurityUtils() {
    }

    /**
     * Get the login of the current user.
     *
     * @return the login of the current user.
     */
    public static Optional<String> getCurrentUserLogin() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return Optional.ofNullable(extractPrincipal(securityContext.getAuthentication()));
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        } else if (authentication.getPrincipal() instanceof UserDetails) {
            UserDetails springSecurityUser = (UserDetails) authentication.getPrincipal();
            return springSecurityUser.getUsername();
        } else if (authentication.getPrincipal() instanceof String) {
            return (String) authentication.getPrincipal();
        }
        return null;
    }

    /**
     * Get the JWT of the current user.
     *
     * @return the JWT of the current user.
     */
    public static Optional<String> getCurrentUserJWT() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return Optional
            .ofNullable(securityContext.getAuthentication())
            .filter(authentication -> authentication.getCredentials() instanceof String)
            .map(authentication -> (String) authentication.getCredentials());
    }

    /**
     * Check if a user is authenticated.
     *
     * @return true if the user is authenticated, false otherwise.
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && getAuthorities(authentication).noneMatch(AuthoritiesConstants.ANONYMOUS::equals);
    }

    /**
     * Checks if the current user has any of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has any of the authorities, false otherwise.
     */
    public static boolean hasCurrentUserAnyOfAuthorities(String... authorities) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (
            authentication != null && getAuthorities(authentication).anyMatch(authority -> Arrays.asList(authorities).contains(authority))
        );
    }

    /**
     * Checks if the current user has none of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has none of the authorities, false otherwise.
     */
    public static boolean hasCurrentUserNoneOfAuthorities(String... authorities) {
        return !hasCurrentUserAnyOfAuthorities(authorities);
    }

    /**
     * Checks if the current user has a specific authority.
     *
     * @param authority the authority to check.
     * @return true if the current user has the authority, false otherwise.
     */
    public static boolean hasCurrentUserThisAuthority(String authority) {
        return hasCurrentUserAnyOfAuthorities(authority);
    }

    private static Stream<String> getAuthorities(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority);
    }
    
    public static String getCurrentUserTenantCode() {
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if(authentication == null || !(authentication.getDetails() instanceof CurrentUserInfo)) {
			return null;
		}
    	CurrentUserInfo currentUserInfo = (CurrentUserInfo) authentication.getDetails();
		return (String) currentUserInfo.getMoreInfo().get(CurrentUserInfoKey.TENANT_CODE);
    }
    
    public static CurrentUserInfo getCurrentUserInfo() {
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if(authentication == null || !(authentication.getDetails() instanceof CurrentUserInfo)) {
			return null;
		}
    	return (CurrentUserInfo) authentication.getDetails();
    }
    
    public static String getCurrentUserCode() {
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if(authentication == null || !(authentication.getDetails() instanceof CurrentUserInfo)) {
			return null;
		}
    	CurrentUserInfo currentUserInfo = (CurrentUserInfo) authentication.getDetails();
		return (String) currentUserInfo.getUserCode();
    }
    
    public static List<String> getCurrentUserRoles() {
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if(authentication == null || !(authentication.getDetails() instanceof CurrentUserInfo)) {
			return new ArrayList<>();
		}
		CurrentUserInfo currentUserInfo = (CurrentUserInfo) authentication.getDetails();
		return (List<String>) currentUserInfo.getMoreInfo().get(CurrentUserInfoKey.AUTHORITIES_KEY);
    }
    
    public static String getCurrentAppCode() {
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if(authentication == null || !(authentication.getDetails() instanceof CurrentUserInfo)) {
			return null;
		}
    	CurrentUserInfo currentUserInfo = (CurrentUserInfo) authentication.getDetails();
		return (String) currentUserInfo.getMoreInfo().get(CurrentUserInfoKey.APP_CODE);
    }
    
    public static String getCurrentUserName() {
        return getCurrentUserLogin().orElseGet(() -> null);
    }

    public static String getCurrentUserEmpCode() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getDetails() instanceof CurrentUserInfo)) {
            return null;
        }
        CurrentUserInfo currentUserInfo = (CurrentUserInfo) authentication.getDetails();
        Map<String, Object> appData = (Map<String, Object>) currentUserInfo.getMoreInfo().get(APPDATA_KEY);
        if(appData ==null || appData.isEmpty()) {
            return null;
        }
        return (String) appData.get("empCode");
    }

    public static String getCurrentUserUnitCode() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getDetails() instanceof CurrentUserInfo)) {
            return null;
        }
        CurrentUserInfo currentUserInfo = (CurrentUserInfo) authentication.getDetails();
        Map<String, Object> appData = (Map<String, Object>) currentUserInfo.getMoreInfo().get(APPDATA_KEY);
        if(appData ==null || appData.isEmpty()) {
            return null;
        }
        return (String) appData.get(CurrentUserInfoKey.UNIT_CODE);
    }
    
    //
    public static final FieldSecCheckUserContext getFieldSecCheckUserContext() {
    	FieldSecCheckUserContext context = new FieldSecCheckUserContext();
    	context.setUsername(getCurrentUserName());
    	CurrentUserInfo userInfo = getCurrentUserInfo();
    	if(userInfo != null) {
    		context.setEmail(userInfo.getEmail());
    		context.setAggId(userInfo.getAggId());
    		context.setUserCode(userInfo.getUserCode());
    		context.setMoreInfo(userInfo.getMoreInfo());
    		String empCode = userInfo.getMoreInfo() != null ? (String) userInfo.getMoreInfo().get("empCode") : null;
    		context.setEmpCode(empCode);
    	}
    	return context;
    }
    
    public static final String buildUserCode(String secmtUsername, String appCode, String tenantCode) {
        return appCode + "." + tenantCode + "." + secmtUsername;
    }

    public static Authentication getBasicAuthentication(String username) {
        Collection<? extends GrantedAuthority> authorities = Arrays.asList(new SimpleGrantedAuthority("ADMIN"));
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, "", authorities);
        CurrentUserInfo details = new CurrentUserInfo(username, "", true, true, true, true, authorities);
        String secmtUsername = username.split("@")[0];
        String userCode = buildUserCode(secmtUsername, Constants.APP_CODE, Constants.TENANT_CODE);
        details.setUserCode(userCode);
        Map<String, Object> moreInfo = new HashMap<>();
        moreInfo.put(CurrentUserInfoKey.APP_CODE, Constants.APP_CODE);
        moreInfo.put(CurrentUserInfoKey.TENANT_CODE, Constants.TENANT_CODE);
        moreInfo.put(CurrentUserInfoKey.USER_NAME, secmtUsername);
        
        details.setMoreInfo(moreInfo);
        authenticationToken.setDetails(details);
        return authenticationToken;
    }
    
    public static <R> R runAsByPassAuth(RunAsWork<R> runAsWork, String uid) {
		Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
		String currentJwt = AuthenticationContext.getJwt();
		try {
			AuthProperties authProperties = ApplicationContextStore.getApplicationContext().getBean(AuthProperties.class);
			String authInfo = authProperties.getOnBehalfAuth().get(uid);
			
			if(authInfo != null) {
				String[] parts = authInfo.split(":");
				String username = parts[0];
				TokenProvider tokenProvider = ApplicationContextStore.getApplicationContext().getBean(TokenProvider.class);
				AuthenticationIService autIService = ApplicationContextStore.getApplicationContext().getBean(AuthenticationIService.class);
				String jwt = autIService.loadToken(username);
				
				if(StringUtils.isEmpty(jwt) || !autIService.validateToken(jwt)) {
					LOGGER.debug("Trying to authentication with username={}", username);
					Authentication auth = getBasicAuthentication(username);
					
					jwt = tokenProvider.createToken(auth, false);
					
					if(jwt != null) {
						LOGGER.debug("JWT TOKEN: {}", jwt);
						autIService.saveToken(username, jwt);
						
						AuthenticationContext.setJwt(jwt);
						SecurityContextHolder.getContext().setAuthentication(auth);
					}
					else {
						throw new IntegrationRuntimeException("Failed to authenticate with info: " + authInfo);
					}
				}
				else {
					Authentication auth = tokenProvider.getAuthentication(jwt);
					SecurityContextHolder.getContext().setAuthentication(auth);
					AuthenticationContext.setJwt(jwt);
				}
			}
			
			//
			R result = runAsWork.doWork();
			return result;
		} catch (Throwable exception) {
			LOGGER.error("Failed to run work task", exception);
			if ((exception instanceof RuntimeException)) {
				throw ((RuntimeException) exception);
			}

			throw new RuntimeException("Error during run as.", exception);

		} finally {
			SecurityContextHolder.getContext().setAuthentication(currentAuth);
			AuthenticationContext.setJwt(currentJwt);
		}
	}
}
