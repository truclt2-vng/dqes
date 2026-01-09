/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.exception;


public class IntegrationRuntimeException extends RuntimeException {

	private static final long serialVersionUID = -2544421403037635307L;
	private int statusCode;
	private String resp;
	private String input;

	/**
	 * 
	 */
	public IntegrationRuntimeException() {
		super();
	}

	public IntegrationRuntimeException(String msg) {
		super(msg);
	}

	public IntegrationRuntimeException(Throwable ex) {
		super(ex);
	}

	public IntegrationRuntimeException(String msg, Throwable ex) {
		super(msg, ex);
	}
	
	public IntegrationRuntimeException(String msg, Throwable ex, String input) {
		super(msg, ex);
		this.input = input;
	}

	/**
	 * @param message
	 * @param cause
	 * @param statusCode
	 */
	public IntegrationRuntimeException(String message, Throwable cause, int statusCode, String resp) {
		super(message, cause);
		this.statusCode = statusCode;
		this.resp = resp;
	}
	
	/**
	 * @param message
	 * @param cause
	 * @param statusCode
	 */
	public IntegrationRuntimeException(String message, Throwable cause, int statusCode, String input, String resp) {
		super(message, cause);
		this.statusCode = statusCode;
		this.input = input;
		this.resp = resp;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	public String getResp() {
		return resp;
	}

	public void setResp(String resp) {
		this.resp = resp;
	}

	public String getInput() {
		return input;
	}

	public void setInput(String input) {
		this.input = input;
	}

	@Override
	public String toString() {
		String str = super.toString();
		str += ";\n [STATUS: " + statusCode + "; INPUT: " + input + "; RESP: " + resp + "]";
		return str;
	}
}
