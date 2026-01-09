/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.constant;

import com.a4b.core.rest.data.ErrorConstants;


public interface ErrorCodes extends ErrorConstants {

	// common
	String NO_PERMISSION = "no_permission";
	String ERROR_INVALID_VALUE = "error.constraints.invalidValue";
	String ERROR_NOT_FOUND = "error.constraints.notFound";
	String ERROR_NOT_ENABLED = "error.constraints.notEnabled";
	String ERROR_UNIQUE = "error.constraints.unique";
	String ERROR_IMMUTABLE = "error.constraints.immutable";
	String VALIDATION_EXTRA_INFO_KEY_NOT_FOUND = PREFIX_VALIDATION + "extraInfo.keyNotFound";
	String VALIDATION_EXTRA_INFO_DATATYPE_NOT_MATCH = PREFIX_VALIDATION + "extraInfo.datatypeNotMatch";
	String VALIDATION_EXTRA_INFO_INVALID_VALUE = PREFIX_VALIDATION + "extraInfo.invalidValue";

	String VALIDATION_NOT_FOUND = PREFIX_VALIDATION + "notFound";
    String VALIDATION_DUPLICATE = PREFIX_VALIDATION + "duplicated";
	String VALIDATION_INVALID = PREFIX_VALIDATION + "invalid";
	String VALIDATION_IMMUTABLE = PREFIX_VALIDATION + "immutable";
}
