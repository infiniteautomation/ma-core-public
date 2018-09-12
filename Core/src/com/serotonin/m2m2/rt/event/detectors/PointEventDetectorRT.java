/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.serotonin.m2m2.rt.event.detectors;

import java.util.HashMap;
import java.util.Map;

import com.serotonin.m2m2.db.dao.DataPointTagsDao;
import com.serotonin.m2m2.rt.dataImage.DataPointListener;
import com.serotonin.m2m2.rt.dataImage.PointValueTime;
import com.serotonin.m2m2.rt.event.type.DataPointEventType;
import com.serotonin.m2m2.rt.event.type.EventType;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.event.detector.AbstractPointEventDetectorVO;

abstract public class PointEventDetectorRT<T extends AbstractPointEventDetectorVO<T>> extends AbstractEventDetectorRT<T> implements DataPointListener {
    
	/**
	 * @param vo
	 */
	public PointEventDetectorRT(T vo) {
		super(vo);
	}

    protected EventType getEventType() {
        DataPointEventType et = new DataPointEventType(vo.njbGetDataPoint().getId(), vo.getId());
        if (!vo.isRtnApplicable())
            et.setDuplicateHandling(EventType.DuplicateHandling.ALLOW);
        return et;
    }

    protected Map<String, Object> createEventContext() {
        Map<String, Object> context = new HashMap<String, Object>();
        context.put("pointEventDetector", vo);
        DataPointVO dataPointVo = vo.njbGetDataPoint();
        //Load the tags if they have not already been loaded
        if(dataPointVo.getTags() == null)
            dataPointVo.setTags(DataPointTagsDao.getInstance().getTagsForDataPointId(dataPointVo.getId()));
        context.put("point", dataPointVo);
        return context;
    }

    public abstract boolean isEventActive();

    @Override
    public String getListenerName(){
    	return vo.getXid();
    }
    //
    //
    // Lifecycle interface
    //
    public void initialize() {
        // no op
    }

    public void terminate() {
        // no op
    }

    public void joinTermination() {
        // no op
    }

    //
    //
    // Point listener interface
    //
	@Override
    public void pointChanged(PointValueTime oldValue, PointValueTime newValue) {
        // no op
    }
	@Override
    public void pointSet(PointValueTime oldValue, PointValueTime newValue) {
        // no op
    }
	@Override
    public void pointUpdated(PointValueTime newValue) {
        // no op
    }
	@Override
    public void pointBackdated(PointValueTime value) {
        // no op
    }
	@Override
    public void pointInitialized() {
        // no op
    }
	@Override
    public void pointTerminated() {
        // no op
    }
	@Override
    public void pointLogged(PointValueTime value){
    	//no op
    }
}
