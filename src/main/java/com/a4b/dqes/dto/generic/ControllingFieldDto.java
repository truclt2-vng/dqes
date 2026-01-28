/**
 * Created: Jun 05, 2025 4:15:00 PM
 * Copyright © 2025 by A4B. All rights reserved
 */
package com.a4b.dqes.dto.generic;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ControllingFieldDto implements Serializable{
	private Long id;

	@Hidden
	private UUID aggId;
	
	@Hidden
	private Date createDate;
	@Hidden
	private Date makerDate;
	@Hidden
	private String makerId;
	@Hidden
	private Date updateDate;
	@Hidden
	private String updateId;
	@Hidden
	private String checkerId;
	@Hidden
	private Date checkerDate;
	@Hidden
	private String tenantCode;
	@Hidden
	private String appCode;
	@Hidden
	private Long modNo;
	@Hidden
	private Long recordOrder;
	@Hidden
	private String ftsStringValue;
	@Hidden
	private String authStatus;
	@Hidden
	private String recordStatus;
}
