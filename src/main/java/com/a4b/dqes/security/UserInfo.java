/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.security;

/**
 * Chua thong tin nguoi dung da dang nhap he thong
 *
 */
public class UserInfo extends CommonUserInfo {
	public UserInfo(com.a4b.dqes.security.User user) {
		super(user);
	}

	/**
	 *
	 */
	private static final long serialVersionUID = 1;
	private Long businessUnitId;
	private String userDomain;
	private String reversedUserDomain;

	public Long getBusinessUnitId() {
		return businessUnitId;
	}

	public void setBusinessUnitId(Long businessUnitId) {
		this.businessUnitId = businessUnitId;
	}

	/**
	 * @return the userDomain
	 */
	public String getUserDomain() {
		return userDomain;
	}

	/**
	 * @param userDomain
	 *            the userDomain to set
	 */
	public void setUserDomain(String userDomain) {
		this.userDomain = userDomain;
	}

	/**
	 * @return the reversedUserDomain
	 */
	public String getReversedUserDomain() {
		return reversedUserDomain;
	}

	/**
	 * @param reversedUserDomain
	 *            the reversedUserDomain to set
	 */
	public void setReversedUserDomain(String reversedUserDomain) {
		this.reversedUserDomain = reversedUserDomain;
	}
}
