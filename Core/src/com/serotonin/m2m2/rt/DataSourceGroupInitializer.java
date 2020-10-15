/**
 * Copyright (C) 2015 Infinite Automation Software. All rights reserved.
 *
 * @author Terry Packer
 */
package com.serotonin.m2m2.rt;

import java.util.List;
import java.util.concurrent.ExecutorService;

import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.module.DataSourceDefinition.StartPriority;
import com.serotonin.m2m2.vo.dataSource.DataSourceVO;

/**
 * This class is used at startup to initialize data sources in parallel.
 * The group is generally a list of all data sources with the same priority level.
 * The group is not initialized until all data sources have either started or failed to start.
 *
 * @author Terry Packer
 * @author Jared Wiltshire
 *
 */
public class DataSourceGroupInitializer extends GroupInitializer<DataSourceVO, DataSourceVO> {
    private final StartPriority startPriority;

    public DataSourceGroupInitializer(boolean useMetrics, ExecutorService executor, int maxConcurrency, StartPriority startPriority) {
        super(useMetrics, executor, maxConcurrency);
        this.startPriority = startPriority;
    }

    @Override
    public List<DataSourceVO> initialize(List<DataSourceVO> items) {
        long startTs = Common.timer.currentTimeMillis();
        if (useMetrics && log.isInfoEnabled()) {
            log.info("Initializing {} {} priority data sources in {} threads",
                    items.size(), startPriority, maxConcurrency);
        }
        List<DataSourceVO> result = super.initialize(items);
        if (useMetrics && log.isInfoEnabled()) {
            log.info("Initialization of {} {} priority data sources in {} threads took {} ms",
                    items.size(), startPriority, maxConcurrency, Common.timer.currentTimeMillis() - startTs);
        }
        return result;
    }

    @Override
    protected DataSourceVO apply(DataSourceVO dataSource) {
        if (Common.runtimeManager.initializeDataSourceStartup(dataSource)) {
            return dataSource;
        } else {
            throw new RuntimeException("Failed to initialize point " + dataSource.getXid());
        }
    }

}
