/**
 * Created: Oct 16, 2024 11:21:48 AM
 * Copyright © 2024 by A4B. All rights reserved
 */
package com.a4b.dqes.web.rest.errors;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
@Order(1)
public class ResponseEntityExceptionHandlerExt extends ResponseEntityExceptionHandler{
    
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        
        logger.error("HttpMessageNotReadableException",ex);
        return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }
}
