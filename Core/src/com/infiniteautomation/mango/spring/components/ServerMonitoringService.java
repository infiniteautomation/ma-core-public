/*
 * Copyright (C) 2019 Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.mango.spring.components;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import com.infiniteautomation.mango.monitor.DoubleMonitor;
import com.infiniteautomation.mango.monitor.IntegerMonitor;
import com.infiniteautomation.mango.monitor.ValueMonitorOwner;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.IMangoLifecycle;
import com.serotonin.m2m2.ServerStatus;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.web.mvc.spring.security.MangoSessionRegistry;

/**
 * @author Jared Wiltshire
 * @author Terry Packer
 */
@Service
public class ServerMonitoringService implements ValueMonitorOwner {

    //Jetty Server metrics
    public static final String SERVER_THREADS = "internal.monitor.SERVER_THREADS";
    public static final String SERVER_IDLE_THREADS = "internal.monitor.SERVER_IDLE_THREADS";
    public static final String SERVER_QUEUE_SIZE = "internal.monitor.SERVER_QUEUE_SIZE";
    
    private final IntegerMonitor threads = new IntegerMonitor(SERVER_THREADS, new TranslatableMessage(SERVER_THREADS), this);
    private final IntegerMonitor idleThreads = new IntegerMonitor(SERVER_IDLE_THREADS, new TranslatableMessage(SERVER_IDLE_THREADS), this);
    private final IntegerMonitor queueSize = new IntegerMonitor(SERVER_QUEUE_SIZE, new TranslatableMessage(SERVER_QUEUE_SIZE), this);

    //JVM Metrics
    public static final String MAX_STACK_HEIGHT_MONITOR_ID = "com.serotonin.m2m2.rt.maint.WorkItemMonitor.maxStackHeight";
    public static final String THREAD_COUNT_MONITOR_ID = "com.serotonin.m2m2.rt.maint.WorkItemMonitor.threadCount";

    private final IntegerMonitor maxStackHeight = new IntegerMonitor(MAX_STACK_HEIGHT_MONITOR_ID,
            new TranslatableMessage("internal.monitor.MONITOR_STACK_HEIGHT"), this);
    private final IntegerMonitor threadCount = new IntegerMonitor(THREAD_COUNT_MONITOR_ID,
            new TranslatableMessage("internal.monitor.MONITOR_THREAD_COUNT"), this);
    private final IntegerMonitor javaMaxMemory = new IntegerMonitor("java.lang.Runtime.maxMemory",
            new TranslatableMessage("java.monitor.JAVA_MAX_MEMORY"), this, true);
    private final IntegerMonitor javaUsedMemory = new IntegerMonitor("java.lang.Runtime.usedMemory",
            new TranslatableMessage("java.monitor.JAVA_USED_MEMORY"), this);
    private final IntegerMonitor javaFreeMemory = new IntegerMonitor("java.lang.Runtime.freeMemory",
            new TranslatableMessage("java.monitor.JAVA_FREE_MEMORY"), this);
    private final IntegerMonitor javaAvailableProcessors = new IntegerMonitor("java.lang.Runtime.availableProcessors",
            new TranslatableMessage("java.monitor.JAVA_PROCESSORS"), this, true);

    //DB Metrics
    public static final String DB_ACTIVE_CONNECTIONS_MONITOR_ID = "com.serotonin.m2m2.rt.maint.WorkItemMonitor.dbActiveConnections";
    public static final String DB_IDLE_CONNECTIONS_MONITOR_ID = "com.serotonin.m2m2.rt.maint.WorkItemMonitor.dbIdleConnections";
    
    private final IntegerMonitor dbActiveConnections = new IntegerMonitor(DB_ACTIVE_CONNECTIONS_MONITOR_ID,
            new TranslatableMessage("internal.monitor.DB_ACTIVE_CONNECTIONS"), this);
    private final IntegerMonitor dbIdleConnections = new IntegerMonitor(DB_IDLE_CONNECTIONS_MONITOR_ID,
            new TranslatableMessage("internal.monitor.DB_IDLE_CONNECTIONS"), this);

    //Work Item Metrics
    public static final String HIGH_PROIRITY_ACTIVE_MONITOR_ID = "com.serotonin.m2m2.rt.maint.WorkItemMonitor.highPriorityActive";
    public static final String HIGH_PRIORITY_SCHEDULED_MONITOR_ID = "com.serotonin.m2m2.rt.maint.WorkItemMonitor.highPriorityScheduled";
    public static final String HIGH_PROIRITY_WAITING_MONITOR_ID = "com.serotonin.m2m2.rt.maint.WorkItemMonitor.highPriorityWaiting";
    public static final String MEDIUM_PROIRITY_ACTIVE_MONITOR_ID = "com.serotonin.m2m2.rt.maint.WorkItemMonitor.mediumPriorityActive";
    public static final String MEDIUM_PROIRITY_WAITING_MONITOR_ID = "com.serotonin.m2m2.rt.maint.WorkItemMonitor.mediumPriorityWaiting";
    public static final String LOW_PROIRITY_ACTIVE_MONITOR_ID = "com.serotonin.m2m2.rt.maint.WorkItemMonitor.lowPriorityActive";
    public static final String LOW_PROIRITY_WAITING_MONITOR_ID = "com.serotonin.m2m2.rt.maint.WorkItemMonitor.lowPriorityWaiting";

    private final IntegerMonitor highPriorityActive =
            new IntegerMonitor(HIGH_PROIRITY_ACTIVE_MONITOR_ID, new TranslatableMessage("internal.monitor.MONITOR_HIGH_ACTIVE"), this);
    private final IntegerMonitor highPriorityScheduled =
            new IntegerMonitor(HIGH_PRIORITY_SCHEDULED_MONITOR_ID, new TranslatableMessage("internal.monitor.MONITOR_HIGH_SCHEDULED"), this);
    private final IntegerMonitor highPriorityWaiting =
            new IntegerMonitor(HIGH_PROIRITY_WAITING_MONITOR_ID, new TranslatableMessage("internal.monitor.MONITOR_HIGH_WAITING"), this);
    private final IntegerMonitor mediumPriorityActive =
            new IntegerMonitor(MEDIUM_PROIRITY_ACTIVE_MONITOR_ID, new TranslatableMessage("internal.monitor.MONITOR_MEDIUM_ACTIVE"), this);
    private final IntegerMonitor mediumPriorityWaiting =
            new IntegerMonitor(MEDIUM_PROIRITY_WAITING_MONITOR_ID, new TranslatableMessage("internal.monitor.MONITOR_MEDIUM_WAITING"), this);
    private final IntegerMonitor lowPriorityActive =
            new IntegerMonitor(LOW_PROIRITY_ACTIVE_MONITOR_ID, new TranslatableMessage("internal.monitor.MONITOR_LOW_ACTIVE"), this);
    private final IntegerMonitor lowPriorityWaiting =
            new IntegerMonitor(LOW_PROIRITY_WAITING_MONITOR_ID, new TranslatableMessage("internal.monitor.MONITOR_LOW_WAITING"), this);

    //System Uptime
    public static final String SYSTEM_UPTIME_MONITOR_ID = "mango.system.uptime";
    private final DoubleMonitor uptime = new DoubleMonitor(SYSTEM_UPTIME_MONITOR_ID, new TranslatableMessage("internal.monitor.SYSTEM_UPTIME"), this);

    //User Sessions
    public static final String USER_SESSION_MONITOR_ID = MangoSessionRegistry.class.getCanonicalName() + ".COUNT";
    private final IntegerMonitor userSessions = new IntegerMonitor(USER_SESSION_MONITOR_ID, new TranslatableMessage("internal.monitor.USER_SESSION_COUNT"), this);

    //Load average
    public static final String LOAD_AVERAGE_MONITOR_ID = "internal.monitor.LOAD_AVERAGE";
    private final OperatingSystemMXBean osBean;
    private final DoubleMonitor loadAverageMonitor = new DoubleMonitor(LOAD_AVERAGE_MONITOR_ID, new TranslatableMessage("systemInfo.loadAverageDesc"), this);
    
    private final int mb = 1024*1024;
    
    private final ScheduledExecutorService scheduledExecutor;
    private final long period;
    private volatile ScheduledFuture<?> scheduledFuture;
    private final IMangoLifecycle lifecycle;

    @Autowired
    private ServerMonitoringService(ScheduledExecutorService scheduledExecutor, @Value("${internal.monitor.pollPeriod:10000}") long period, IMangoLifecycle lifecycle) {
        this.scheduledExecutor = scheduledExecutor;
        this.period = period;

        Common.MONITORED_VALUES.addIfMissingStatMonitor(threads);
        Common.MONITORED_VALUES.addIfMissingStatMonitor(idleThreads);
        Common.MONITORED_VALUES.addIfMissingStatMonitor(queueSize);

        Common.MONITORED_VALUES.addIfMissingStatMonitor(highPriorityActive);
        Common.MONITORED_VALUES.addIfMissingStatMonitor(highPriorityScheduled);
        Common.MONITORED_VALUES.addIfMissingStatMonitor(highPriorityWaiting);

        Common.MONITORED_VALUES.addIfMissingStatMonitor(mediumPriorityActive);
        Common.MONITORED_VALUES.addIfMissingStatMonitor(mediumPriorityWaiting);

        Common.MONITORED_VALUES.addIfMissingStatMonitor(lowPriorityActive);
        Common.MONITORED_VALUES.addIfMissingStatMonitor(lowPriorityWaiting);

        Common.MONITORED_VALUES.addIfMissingStatMonitor(maxStackHeight);
        Common.MONITORED_VALUES.addIfMissingStatMonitor(threadCount);
        Common.MONITORED_VALUES.addIfMissingStatMonitor(dbActiveConnections);
        Common.MONITORED_VALUES.addIfMissingStatMonitor(dbIdleConnections);
        Common.MONITORED_VALUES.addIfMissingStatMonitor(javaFreeMemory);
        Common.MONITORED_VALUES.addIfMissingStatMonitor(javaMaxMemory);
        Common.MONITORED_VALUES.addIfMissingStatMonitor(javaAvailableProcessors);
        Common.MONITORED_VALUES.addIfMissingStatMonitor(uptime);

        Common.MONITORED_VALUES.addIfMissingStatMonitor(userSessions);

        //Set the available processors, we don't need to poll this
        javaAvailableProcessors.setValue(Runtime.getRuntime().availableProcessors());
        
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        Common.MONITORED_VALUES.addIfMissingStatMonitor(loadAverageMonitor); 
        
        this.lifecycle = lifecycle;
    }

    @PostConstruct
    private void postConstruct() {
        this.scheduledFuture = scheduledExecutor.scheduleAtFixedRate(this::doPoll, 0, this.period, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    private void preDestroy() {
        ScheduledFuture<?> scheduledFuture = this.scheduledFuture;
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }

    private void doPoll() {
        ServerStatus status = lifecycle.getServerStatus();
        threads.setValue(status.getThreads());
        idleThreads.setValue(status.getIdleThreads());
        queueSize.setValue(status.getQueueSize());
        
        if(Common.backgroundProcessing != null){
            highPriorityActive.setValue(Common.backgroundProcessing.getHighPriorityServiceActiveCount());
            highPriorityScheduled.setValue(Common.backgroundProcessing.getHighPriorityServiceScheduledTaskCount());
            highPriorityWaiting.setValue(Common.backgroundProcessing.getHighPriorityServiceQueueSize());

            mediumPriorityActive.setValue(Common.backgroundProcessing.getMediumPriorityServiceActiveCount());
            mediumPriorityWaiting.setValue(Common.backgroundProcessing.getMediumPriorityServiceQueueSize());

            lowPriorityActive.setValue(Common.backgroundProcessing.getLowPriorityServiceActiveCount());
            lowPriorityWaiting.setValue(Common.backgroundProcessing.getLowPriorityServiceQueueSize());
        }



        // Check the stack heights
        int max = 0;
        Collection<StackTraceElement[]> stacks = Thread.getAllStackTraces().values();
        int count = stacks.size();
        for (StackTraceElement[] stack : stacks) {
            if (max < stack.length)
                max = stack.length;
            if (stack.length == 0)
                // Don't include inactive threads
                count--;
        }
        threadCount.setValue(count);
        maxStackHeight.setValue(max);

        if(Common.databaseProxy != null){
            dbActiveConnections.setValue(Common.databaseProxy.getActiveConnections());
            dbIdleConnections.setValue(Common.databaseProxy.getIdleConnections());
        }

        //In MB
        Runtime rt = Runtime.getRuntime();
        javaMaxMemory.setValue((int)(rt.maxMemory()/mb));
        javaUsedMemory.setValue((int)(rt.totalMemory()/mb) -(int)(rt.freeMemory()/mb));
        javaFreeMemory.setValue(javaMaxMemory.getValue() - javaUsedMemory.getValue());

        //Uptime in HRS
        long uptimeMs = Common.timer.currentTimeMillis() - Common.START_TIME;
        Double uptimeHrs = uptimeMs/3600000.0D;
        BigDecimal bd = new BigDecimal(uptimeHrs);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        uptime.setValue(bd.doubleValue());

        //Collect Active User Sessions
        ConfigurableApplicationContext context = (ConfigurableApplicationContext) Common.getRootWebContext();
        if (context != null && context.isActive()) {
            MangoSessionRegistry sessionRegistry = context.getBean(MangoSessionRegistry.class);
            userSessions.setValue(sessionRegistry.getActiveSessionCount());
        } else {
            userSessions.setValue(0);
        }
        
        
        if(this.osBean != null) {
            loadAverageMonitor.setValue(osBean.getSystemLoadAverage());
        }
    }

    @Override
    public void reset(String monitorId) {
        // nop
    }
}