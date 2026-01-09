/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.security;

import java.io.Serializable;
import java.util.Map;


public class FieldSecCheckUserContext implements Serializable {

	private static final long serialVersionUID = 5585893895284164761L;
	private String email;
	private String username;
	private String aggId;
	private String userCode;
	private String empCode;
	private Map<String, Object> moreInfo;

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getAggId() {
		return aggId;
	}

	public void setAggId(String aggId) {
		this.aggId = aggId;
	}

	public String getUserCode() {
		return userCode;
	}

	public void setUserCode(String userCode) {
		this.userCode = userCode;
	}

	public Map<String, Object> getMoreInfo() {
		return moreInfo;
	}

	public void setMoreInfo(Map<String, Object> moreInfo) {
		this.moreInfo = moreInfo;
	}

	public String getEmpCode() {
		return empCode;
	}

	public void setEmpCode(String empCode) {
		this.empCode = empCode;
	}

}
