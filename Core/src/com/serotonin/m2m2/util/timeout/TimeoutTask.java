/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.serotonin.m2m2.util.timeout;

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.serotonin.m2m2.Common;
import com.serotonin.timer.AbstractTimer;
import com.serotonin.timer.OneTimeTrigger;
import com.serotonin.timer.RejectedTaskReason;
import com.serotonin.timer.TimerTask;
import com.serotonin.timer.TimerTrigger;

/**
 * A TimeoutTask is run at HighPriority either now or at some point in the future
 * 
 *
 */
public class TimeoutTask extends TimerTask {
	
	private final Log LOG = LogFactory.getLog(TimeoutTask.class);
	
    private final TimeoutClient client;
    
    public TimeoutTask(long delay, TimeoutClient client) {
        this(new OneTimeTrigger(delay), client);
    }

    public TimeoutTask(Date date, TimeoutClient client) {
        this(new OneTimeTrigger(date), client);
    }

    public TimeoutTask(TimerTrigger trigger, TimeoutClient client) {
        super(trigger, client.getThreadName(), client.getTaskId(), client.getQueueSize());
        this.client = client;
        Common.backgroundProcessing.schedule(this);
    }
    
    /**
     * Timeout Task for simulations using custom timers
     * @param trigger
     * @param client
     * @param timer
     */
    public TimeoutTask(TimerTrigger trigger, TimeoutClient client, AbstractTimer timer) {
        super(trigger, client.getThreadName(), client.getTaskId(), client.getQueueSize());
        this.client = client;
        timer.schedule(this);
    }
    
    @Override
    public void run(long runtime) {
    	try{
    		client.scheduleTimeout(runtime);
    	}catch(Exception e){
    		LOG.error("Uncaught Task Exception", e);
    	}
    }
    
    @Override
    public void rejected(RejectedTaskReason reason) {
        try {
            this.client.rejected(reason);
        }catch(Exception e){
            LOG.error("Uncaught Task Rejection Exception", e);
        }
    }
}
