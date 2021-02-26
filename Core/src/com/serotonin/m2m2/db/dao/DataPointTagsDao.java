/**
 * Copyright (C) 2017 Infinite Automation Software. All rights reserved.
 */

package com.serotonin.m2m2.db.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.BatchBindStep;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.infiniteautomation.mango.db.query.ConditionSortLimit;
import com.infiniteautomation.mango.db.query.ConditionSortLimitWithTagKeys;
import com.infiniteautomation.mango.db.query.RQLToConditionWithTagKeys;
import com.infiniteautomation.mango.db.tables.DataPointTags;
import com.infiniteautomation.mango.db.tables.DataPoints;
import com.infiniteautomation.mango.spring.service.PermissionService;
import com.infiniteautomation.mango.util.LazyInitializer;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.rt.dataImage.DataPointRT;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.permission.PermissionHolder;

import net.jazdw.rql.parser.ASTNode;

/**
 * @author Jared Wiltshire
 */
@Repository()
public class DataPointTagsDao extends BaseDao {
    private static final LazyInitializer<DataPointTagsDao> springInstance = new LazyInitializer<>();

    public static final Name TAGS_PIVOT_ALIAS = DSL.name("tagsPivot");
    public static final String DEVICE_TAG_KEY = "device";
    public static final String NAME_TAG_KEY = "name";

    private final DataPointTags table = DataPointTags.DATA_POINT_TAGS;
    private final DataPoints dataPointTable = DataPoints.DATA_POINTS;
    private final PermissionService permissionService;

    @Autowired
    private DataPointTagsDao(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * Get cached instance from Spring Context
     * @return
     */
    public static DataPointTagsDao getInstance() {
        return springInstance.get(() -> Common.getRuntimeContext().getBean(DataPointTagsDao.class));
    }

    /**
     * Retrieves all tag keys and values from the database for a datapoint.
     * Contains a "name" and "device" key as opposed to the tags retrieved via DataPointVO.getTags().
     *
     * @param dataPointId
     * @return
     */
    public Map<String, String> getTagsForDataPointId(int dataPointId) {
        Select<Record2<String, String>> query = this.create.select(table.tagKey, table.tagValue)
                .from(table)
                .where(table.dataPointId.eq(dataPointId));

        try (Stream<Record2<String, String>> stream = query.stream()) {
            return stream.collect(Collectors.toMap(Record2::value1, Record2::value2));
        }
    }

    public int deleteTagsForDataPointId(int dataPointId) {
        return this.create.deleteFrom(table)
                .where(table.dataPointId.eq(dataPointId))
                .execute();
    }

    public int deleteNameAndDeviceTagsForDataPointId(int dataPointId) {
        return this.create.deleteFrom(table)
                .where(table.dataPointId.eq(dataPointId))
                .and(DSL.or(table.tagKey.eq(NAME_TAG_KEY), table.tagKey.eq(DEVICE_TAG_KEY)))
                .execute();
    }

    /**
     * Inserts tags into the database for a DataPointVO. Also inserts the "name" and "device" tags from the data point properties.
     *
     * @param dataPoint
     * @param tags Should not contain tag keys "name" or "device"
     */
    public void insertTagsForDataPoint(DataPointVO dataPoint, Map<String, String> tags) {
        int dataPointId = dataPoint.getId();
        String name = dataPoint.getName();
        String deviceName = dataPoint.getDeviceName();

        BatchBindStep b = create.batch(
                DSL.insertInto(table)
                .columns(table.dataPointId, table.tagKey, table.tagValue)
                .values((Integer) null, null, null));
        tags.forEach((key, value) -> b.bind(dataPointId, key, value));

        if (name != null && !name.isEmpty()) {
            b.bind(dataPointId, NAME_TAG_KEY, name);
        }
        if (deviceName != null && !deviceName.isEmpty()) {
            b.bind(dataPointId, DEVICE_TAG_KEY, deviceName);
        }

        b.execute();
    }

    /**
     * Only to be used when saving data point tags independently from the DataPointVO itself.
     * The DataPointVO tags must not be null.
     *
     * @param dataPoint
     */
    public void saveDataPointTags(DataPointVO dataPoint) {
        Map<String, String> tags = dataPoint.getTags();
        if (tags == null) throw new IllegalArgumentException("Data point tags cannot be null");

        this.doInTransaction(txStatus -> {
            this.deleteTagsForDataPointId(dataPoint.getId());
            this.insertTagsForDataPoint(dataPoint, tags);

            DataPointRT rt = Common.runtimeManager.getDataPoint(dataPoint.getId());
            if (rt != null) {
                DataPointVO rtVo = rt.getVO();
                rtVo.setTags(tags);
            }

            DataPointDao.getInstance().notifyTagsUpdated(dataPoint);
        });
    }


    public Set<String> getTagKeys(PermissionHolder user) {
        SelectJoinStep<Record1<String>> query = this.create.selectDistinct(table.tagKey)
                .from(table);

        if (!permissionService.hasAdminRole(user)) {
            query = query.join(dataPointTable).on(table.dataPointId.eq(dataPointTable.id));

            ConditionSortLimit csl = new ConditionSortLimit(null, null, null, null);
            query = DataPointDao.getInstance().joinPermissions(query, user);
            try (Stream<Record1<String>> stream = query.where(csl.getCondition()).stream()) {
                return stream.map(Record1::value1).collect(Collectors.toSet());
            }
        }else {
            try (Stream<Record1<String>> stream = query.stream()) {
                return stream.map(Record1::value1).collect(Collectors.toSet());
            }
        }
    }

    public Set<String> getTagValuesForKey(String tagKey, PermissionHolder user) {
        SelectJoinStep<Record1<String>> query = this.create.selectDistinct(table.tagValue)
                .from(table);

        SelectConditionStep<Record1<String>> conditional;
        if (!permissionService.hasAdminRole(user)) {
            query = query.join(dataPointTable).on(table.dataPointId.eq(dataPointTable.id));
            ConditionSortLimit csl = new ConditionSortLimit(table.tagKey.eq(tagKey), null, null, null);
            query = DataPointDao.getInstance().joinPermissions(query, user);
            conditional = query.where(csl.getCondition());
        }else {
            conditional = query.where(table.tagKey.eq(tagKey));
        }

        try (Stream<Record1<String>> stream = conditional.stream()) {
            return stream.map(Record1::value1).collect(Collectors.toSet());
        }
    }

    /**
     * For use in Script to get values for a key
     * @param tagKey
     * @param restrictions
     * @param user
     * @return
     */
    public Set<String> getTagValuesForKey(String tagKey, Map<String, String> restrictions, PermissionHolder user) {
        if (restrictions.isEmpty()) {
            return getTagValuesForKey(tagKey, user);
        }

        Set<String> keys = new HashSet<>(restrictions.keySet());
        keys.add(tagKey);
        Map<String, Name> tagKeyToColumn = tagKeyToColumn(keys);
        Name tagKeyColumn = tagKeyToColumn.get(tagKey);

        List<Condition> conditions = restrictions.entrySet().stream()
                .map(e -> DSL.field(TAGS_PIVOT_ALIAS.append(tagKeyToColumn.get(e.getKey()))).eq(e.getValue()))
                .collect(Collectors.toCollection(ArrayList::new));
        Condition allConditions = DSL.and(conditions);

        return getTagValuesForKey(tagKeyColumn, tagKeyToColumn, allConditions, user);
    }

    /**
     * For use with AST node to get values for a key
     * @param tagKey
     * @param restrictions
     * @param user
     * @return
     */
    public Set<String> getTagValuesForKey(String tagKey, ASTNode restrictions, PermissionHolder user) {
        RQLToConditionWithTagKeys visitor = new RQLToConditionWithTagKeys();
        Name tagKeyColumn = visitor.columnNameForTagKey(tagKey);

        List<Condition> conditionList = new ArrayList<>();
        ConditionSortLimitWithTagKeys conditions = visitor.visit(restrictions);
        if (conditions.getCondition() != null) {
            conditionList.add(conditions.getCondition());
        }
        Condition allConditions = DSL.and(conditionList);

        Map<String, Name> tagKeyToColumn = conditions.getTagKeyToColumn();

        return getTagValuesForKey(tagKeyColumn, tagKeyToColumn, allConditions, user);
    }

    private Set<String> getTagValuesForKey(Name tagKeyColumn, Map<String, Name> tagKeyToColumn, Condition allConditions, PermissionHolder user) {
        Table<Record> tagsPivot = createTagPivotTable(tagKeyToColumn).asTable().as(TAGS_PIVOT_ALIAS);

        SelectJoinStep<Record1<String>> query = this.create
                .selectDistinct(tagsPivot.field(tagKeyColumn, String.class))
                .from(tagsPivot);

        if (!permissionService.hasAdminRole(user)) {
            query = query.join(dataPointTable).on(dataPointTable.id.eq(tagsPivot.field(table.dataPointId)));

            ConditionSortLimit csl = new ConditionSortLimit(allConditions, null, null, null);
            query = DataPointDao.getInstance().joinPermissions(query, user);
            allConditions = csl.getCondition();
        }

        Select<Record1<String>> result = query.where(allConditions);
        try (Stream<Record1<String>> stream = result.stream()) {
            return stream.map(Record1::value1).collect(Collectors.toSet());
        }
    }

    /**
     * This method does not filter the tags based on the data point permissions. It should only be used
     * when joining onto the data points table (the filtering happens there post-join).
     *
     * @param tagKeyToColumn
     * @return
     */
    Table<Record> createTagPivotTable(Map<String, Name> tagKeyToColumn) {
        List<Field<?>> fields = new ArrayList<>(tagKeyToColumn.size() + 1);
        fields.add(table.dataPointId);

        for (Entry<String, Name> entry : tagKeyToColumn.entrySet()) {
            fields.add(DSL.max(DSL.when(table.tagKey.eq(entry.getKey()), table.tagValue)).as(entry.getValue()));
        }

        return DSL.select(fields).from(table)
                .groupBy(table.dataPointId)
                .asTable().as(TAGS_PIVOT_ALIAS);
    }

    /**
     * Maps tag keys to generic keyX to prevent SQL injection
     * @return
     */
    Map<String, Name> tagKeyToColumn(Set<String> tagKeys) {
        int i = 1;
        Map<String, Name> tagNameToColumn = new HashMap<>(tagKeys.size());
        for (String key : tagKeys) {
            String fieldName = String.format("key%d", i++);
            tagNameToColumn.put(key, DSL.name(fieldName));
        }
        return tagNameToColumn;
    }
}
