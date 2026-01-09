/**
 * Created: Oct 30, 2025 2:20:58 PM
 * Copyright © 2025 by A4B. All rights reserved
 */
package com.a4b.dqes.base;


import com.a4b.lib.shared.common.Fields;
import com.a4b.dqes.constant.ErrorCodes;
import com.a4b.dqes.security.SecurityUtils;

import jakarta.validation.ConstraintValidatorContext;

/**
 * Base class providing common validation utilities
 * and security context helpers for all custom validators.
 */
public abstract class BaseValidator {

    /**
     * Adds a constraint violation with the given property node and error code.
     *
     * @param context    The validator context
     * @param property   The property name on which the violation occurs
     * @param errorCode  The error code or message template
     */
    protected void addViolation(ConstraintValidatorContext context, String property, String errorCode) {
        context.buildConstraintViolationWithTemplate(errorCode)
               .addPropertyNode(property)
               .addConstraintViolation();
    }

    protected boolean idNotFoundViolation(ConstraintValidatorContext context) {
        addViolation(context, Fields.id, ErrorCodes.VALIDATION_NOT_FOUND);
        return false;
    }

    protected boolean idHasUnauthorizedStatusViolation(ConstraintValidatorContext context) {
        addViolation(context, Fields.id, ErrorCodes.VALIDATION_INVALID);
        return false;
    }

    protected boolean duplicateViolation(ConstraintValidatorContext context, String property) {
        addViolation(context, property, ErrorCodes.VALIDATION_DUPLICATE);
        return false;
    } 

    protected boolean invalidViolation(ConstraintValidatorContext context, String property) {
        addViolation(context, property, ErrorCodes.VALIDATION_INVALID);
        return false;
    }

    protected boolean authStatusInvalidViolation(ConstraintValidatorContext context) {
        addViolation(context, Fields.authStatus, ErrorCodes.VALIDATION_INVALID);
        return false;
    }

    

    protected boolean immutableViolation(ConstraintValidatorContext context, String property) {
        addViolation(context, property, ErrorCodes.VALIDATION_IMMUTABLE);
        return false;
    }

    /**
     * Returns the current tenant code from the security context.
     */
    protected String getTenantCode() {
        return SecurityUtils.getCurrentUserTenantCode();
    }

    /**
     * Returns the current application code from the security context.
     */
    protected String getAppCode() {
        return SecurityUtils.getCurrentAppCode();
    }

    /**
     * Returns the current username from the security context.
     */
    protected String getUsername() {
        return SecurityUtils.getCurrentUserName();
    }
}

