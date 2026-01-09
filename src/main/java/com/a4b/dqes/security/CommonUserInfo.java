/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.security;

import java.io.Serializable;
import java.util.Date;

public class CommonUserInfo implements Serializable {
	private static final long serialVersionUID = 1L;
	private User user;
	private Date lastLoginTime;

	public CommonUserInfo(User user) {
		this.user = user;
	}

	public User getUser() {
		return this.user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public Date getLastLoginTime() {
		return this.lastLoginTime;
	}

	public void setLastLoginTime(Date lastLoginTime) {
		this.lastLoginTime = lastLoginTime;
	}

	public String getUserName() {
		return this.user.getUserName();
	}
}
