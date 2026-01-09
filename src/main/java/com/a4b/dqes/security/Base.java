/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.security;

import java.io.Serializable;
import java.util.Date;

public class Base implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private long id;
	private Date makerDate;
	private String makerId;
	private Long modNo;
	private String recordStatus;
	private String authStatus;
	private Date checkerDate;
	private String checkerId;
	
	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	public Date getMakerDate() {
		return makerDate;
	}
	public void setMakerDate(Date makerDate) {
		this.makerDate = makerDate;
	}
	public String getMakerId() {
		return makerId;
	}
	public void setMakerId(String makerId) {
		this.makerId = makerId;
	}
	public Long getModNo() {
		return modNo;
	}
	public void setModNo(Long modNo) {
		this.modNo = modNo;
	}
	public String getRecordStatus() {
		return recordStatus;
	}
	public void setRecordStatus(String recordStatus) {
		this.recordStatus = recordStatus;
	}
	public String getAuthStatus() {
		return authStatus;
	}
	public void setAuthStatus(String authStatus) {
		this.authStatus = authStatus;
	}
	public Date getCheckerDate() {
		return checkerDate;
	}
	public void setCheckerDate(Date checkerDate) {
		this.checkerDate = checkerDate;
	}
	public String getCheckerId() {
		return checkerId;
	}
	public void setCheckerId(String checkerId) {
		this.checkerId = checkerId;
	}
}
