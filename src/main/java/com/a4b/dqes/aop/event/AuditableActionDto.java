/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.aop.event;

import java.io.Serializable;
import java.util.Map;


public class AuditableActionDto implements Serializable {

	private static final long serialVersionUID = -4799040164381776946L;
	private String objectId;
	private String eventName;
	private Map<String, Object> inputData;
	private Map<String, Object> outputData;

	public AuditableActionDto() {
		super();
	}

	public AuditableActionDto(String objectId, String eventName, Map<String, Object> inputData) {
		super();
		this.objectId = objectId;
		this.eventName = eventName;
		this.inputData = inputData;
	}

	public AuditableActionDto(String objectId, String eventName, Map<String, Object> inputData,
			Map<String, Object> outputData) {
		super();
		this.objectId = objectId;
		this.eventName = eventName;
		this.inputData = inputData;
		this.outputData = outputData;
	}

	public String getObjectId() {
		return objectId;
	}

	public void setObjectId(String objectId) {
		this.objectId = objectId;
	}

	public String getEventName() {
		return eventName;
	}

	public Map<String, Object> getInputData() {
		return inputData;
	}

	public void setEventName(String eventName) {
		this.eventName = eventName;
	}

	public void setInputData(Map<String, Object> inputData) {
		this.inputData = inputData;
	}

	public Map<String, Object> getOutputData() {
		return outputData;
	}

	public void setOutputData(Map<String, Object> outputData) {
		this.outputData = outputData;
	}

}
