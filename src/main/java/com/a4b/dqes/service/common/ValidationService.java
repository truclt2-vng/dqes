/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.service.common;

import java.util.Set;

import org.axonframework.messaging.interceptors.JSR303ViolationException;
import org.springframework.stereotype.Component;

import com.a4b.core.validation.constraints.group.GroupLevel;
import com.a4b.core.validation.constraints.group.GroupOrder;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;


@Component
public class ValidationService {

	private final ValidatorFactory validatorFactory;
	/**
	 * 
	 */
	public ValidationService() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
	}
	
	public void validate(Object obj) {
		Validator validator = validatorFactory.getValidator();
        Set<ConstraintViolation<Object>> violations = validateMessage(obj, validator);
        if (violations != null && !violations.isEmpty()) {
            throw new JSR303ViolationException(violations);
        }
	}
	
	protected Set<ConstraintViolation<Object>> validateMessage(Object obj, Validator validator) {
    	if (obj.getClass().getAnnotation(GroupOrder.class) != null) {
			return validator.validate(obj, GroupLevel.class);
		} else {
			return validator.validate(obj);
		}
    }
}
