/*
 * Copyright (C) 2019 Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.mango.util.exception;

import com.serotonin.m2m2.i18n.TranslatableMessage;

public class FeatureDisabledException extends TranslatableRuntimeException {
    private static final long serialVersionUID = 1L;

    public FeatureDisabledException(TranslatableMessage translatableMessage) {
        super(translatableMessage);
    }
}