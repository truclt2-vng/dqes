/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.exception;


public class DqesRuntimeException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 309753300367394107L;

	public DqesRuntimeException() {
		super();
	}

	public DqesRuntimeException(String msg) {
		super(msg);
	}

	public DqesRuntimeException(String msg, Throwable ex) {
		super(msg, ex);
	}

	public DqesRuntimeException(Throwable ex) {
		super(ex);
	}
}
