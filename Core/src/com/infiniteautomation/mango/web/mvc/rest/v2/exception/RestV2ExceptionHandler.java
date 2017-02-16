/**
 * Copyright (C) 2015 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.infiniteautomation.mango.web.mvc.rest.v2.exception;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.NestedRuntimeException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.infiniteautomation.mango.web.mvc.rest.v2.model.exception.GenericRestExceptionModel;
import com.serotonin.m2m2.i18n.TranslatableMessage;

/**
 * 
 * Class to handle REST Specific Errors and present the user with a Model
 * and also log the errors neatly in our logs
 * 
 * @author Terry Packer
 *
 */
@ControllerAdvice
public class RestV2ExceptionHandler extends ResponseEntityExceptionHandler {
	 protected Log LOG = LogFactory.getLog(RestV2ExceptionHandler.class);
	
    @ExceptionHandler({ 
    	ForbiddenAccessRestException.class,
    	UnauthorizedRestException.class
    	})
    protected ResponseEntity<Object> handleMangoError(Exception e, WebRequest request) {
    	//Since all Exceptions handled by this method extend AbstractRestV2Exception we don't need to check type
    	AbstractRestV2Exception ex = (AbstractRestV2Exception)e;
    	return handleExceptionInternal(e, ex.getBodyModel(), new HttpHeaders(), ex.getStatus(), request);
    }
    
    /* (non-Javadoc)
     * @see org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler#handleExceptionInternal(java.lang.Exception, java.lang.Object, org.springframework.http.HttpHeaders, org.springframework.http.HttpStatus, org.springframework.web.context.request.WebRequest)
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
    		Object body, HttpHeaders headers, HttpStatus status,
    		WebRequest request) {
    	
    	//Set the content type
    	headers.setContentType(MediaType.APPLICATION_JSON);
    	
    	//To strip off the double messages generated by this...
        if(ex instanceof NestedRuntimeException)
        	ex = (Exception) ((NestedRuntimeException) ex).getMostSpecificCause();

    	LOG.error(ex.getMessage(), ex);

    	//If no body provided
        if(body == null)
        	body = new GenericRestExceptionModel(status.value(), new TranslatableMessage("common.default", status.getReasonPhrase()));
        
        
        return new ResponseEntity<Object>(body, headers, status);
    }
    
}
