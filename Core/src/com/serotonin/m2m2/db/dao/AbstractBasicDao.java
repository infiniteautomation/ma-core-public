/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */
package com.serotonin.m2m2.db.dao;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Identity;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.SelectConnectByStep;
import org.jooq.SelectJoinStep;
import org.jooq.SelectLimitStep;
import org.jooq.SelectSelectStep;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.NoDataFoundException;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.infiniteautomation.mango.db.query.ConditionSortLimit;
import com.infiniteautomation.mango.db.query.RQLSubSelectCondition;
import com.infiniteautomation.mango.db.query.RQLToCondition;
import com.infiniteautomation.mango.db.tables.MintermsRoles;
import com.infiniteautomation.mango.db.tables.PermissionsMinterms;
import com.infiniteautomation.mango.monitor.AtomicIntegerMonitor;
import com.infiniteautomation.mango.spring.events.DaoEvent;
import com.infiniteautomation.mango.spring.events.DaoEventType;
import com.infiniteautomation.mango.spring.service.PermissionService;
import com.infiniteautomation.mango.util.RQLUtils;
import com.serotonin.ModuleNotLoadedException;
import com.serotonin.log.LogStopWatch;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.vo.AbstractBasicVO;
import com.serotonin.m2m2.vo.permission.PermissionHolder;
import com.serotonin.m2m2.vo.role.Role;

import net.jazdw.rql.parser.ASTNode;

/**
 * Provides an API to retrieve, update and save VO objects from and to the
 * database.
 *
 * @author Jared Wiltshire
 * @author Terry Packer
 */
public abstract class AbstractBasicDao<T extends AbstractBasicVO, R extends Record, TABLE extends Table<R>> extends BaseDao implements AbstractBasicVOAccess<T> {
    protected Log LOG = LogFactory.getLog(getClass());

    // Retry all insert/update/delete that throw transient exceptions (e.g. transactions that deadlock)
    protected final int transactionRetries = Common.envProps.getInt("db.transaction.retries", 5);

    protected final TABLE table;
    protected final ObjectMapper mapper;
    protected final ApplicationEventPublisher eventPublisher;
    protected final PermissionService permissionService;

    //Monitor for count of table
    protected final AtomicIntegerMonitor countMonitor;

    protected final Map<String, Field<?>> fieldMap;
    protected final Map<String, RQLSubSelectCondition> subSelectMap;
    protected final Map<String, Function<Object, Object>> valueConverterMap;

    public AbstractBasicDao(TABLE table, ObjectMapper mapper, ApplicationEventPublisher publisher, PermissionService permissionService) {
        this(table, null, mapper, publisher, permissionService);
    }

    /**
     * @param table - table definition
     * @param countMonitorName - If not null create a monitor to track table row count
     * @param mapper
     * @param publisher
     * @param permissionService
     */
    public AbstractBasicDao(TABLE table,
                            TranslatableMessage countMonitorName,
                            ObjectMapper mapper, ApplicationEventPublisher publisher, PermissionService permissionService) {

        this.table = table;
        this.mapper = mapper;
        this.eventPublisher = publisher;
        this.permissionService = permissionService;

        // Map of potential RQL property names to db fields
        this.fieldMap = unmodifiableMap(createFieldMap());

        // Map of potential property names to sub select conditions
        this.subSelectMap = unmodifiableMap(createSubSelectMap());

        // Map of properties to their QueryAttribute
        this.valueConverterMap = unmodifiableMap(createValueConverterMap());

        //Setup Monitors
        if(countMonitorName != null) {
            this.countMonitor = Common.MONITORED_VALUES.create(this.getClass().getCanonicalName() + ".COUNT")
                    .name(countMonitorName)
                    .value(this.count())
                    .uploadToStore(true)
                    .buildAtomic();
        }else{
            this.countMonitor = null;
        }
    }

    /**
     * Converts a VO object into a jOOQ {@link Record} for insert/update of DB.
     *
     * @param vo
     *            to convert
     * @return object array
     */
    protected abstract Record toRecord(T vo);

    public abstract @NonNull T mapRecord(@NonNull Record record);

    /**
     * Maps the record safely, catching exceptions
     * @param record SQL record
     * @return the mapped record as a VO type, or null if an exception occurred
     */
    public @Nullable T mapRecordSafe(@NonNull Record record) {
        try {
            return mapRecord(record);
        } catch (Exception e) {
            handleMappingException(e, record);
        }
        return null;
    }

    /**
     * Maps the record (safely, catching exceptions) then loads the relational data.
     * @param record SQL record
     * @return the mapped record as a VO type, or null if an exception occurred
     */
    @Nullable
    public T mapRecordLoadRelationalData(@NonNull Record record) {
        T result = mapRecordSafe(record);
        if (result != null) {
            loadRelationalData(result);
        }
        return result;
    }

    protected void handleMappingException(@NonNull Exception e, @NonNull Record record) {
        if (e.getCause() instanceof ModuleNotLoadedException) {
            //We will log and continue as to not prevent someone from loading module based VOs for
            // which the modules are actually installed.
            LOG.error("Failed to map SQL record due to missing module", e.getCause());
        } else {
            LOG.error("Failed to map SQL record", e);
        }
    }

    @Override
    public boolean delete(int id) {
        return delete(get(id));
    }

    @Override
    public boolean delete(T vo) {
        if (vo != null) {
            int deleted = 0;
            int tries = transactionRetries;
            while (tries-- > 0) {
                try {
                    deleted = withLockedRow(vo.getId(), (txStatus) -> {
                        deleteRelationalData(vo);
                        int result = create.deleteFrom(table).where(getIdField().eq(vo.getId())).execute();
                        if (result > 0) {
                            deletePostRelationalData(vo);
                        }
                        return result;
                    });
                    break;
                } catch (org.jooq.exception.DataAccessException e) {
                    if (!(e.getCause() instanceof SQLTransientException) || tries == 0) {
                        throw e;
                    }
                } catch (TransientDataAccessException e) {
                    if (tries == 0) {
                        throw e;
                    }
                }
            }

            if (this.countMonitor != null) {
                this.countMonitor.addValue(-deleted);
            }

            if (deleted > 0) {
                this.publishEvent(createDaoEvent(DaoEventType.DELETE, vo, null));
            }

            return deleted > 0;
        }
        return false;
    }

    public Field<Integer> getIdField() {
        Identity<? extends Record, ?> identity = table.getIdentity();
        if (identity != null) {
            TableField<? extends Record, ?> field = identity.getField();
            if (field.getDataType().isNumeric()) {
                return field.coerce(Integer.class);
            }
        }
        return null;
    }

    @Override
    public void deleteRelationalData(T vo) { }

    @Override
    public void deletePostRelationalData(T vo) { }

    @Override
    public void insert(T vo) {
        int tries = transactionRetries;
        while (tries-- > 0) {
            try {
                doInTransaction(status -> {
                    savePreRelationalData(null, vo);

                    int id = create.insertInto(table)
                            .set(toRecord(vo))
                            .returningResult(getIdField())
                            .fetchOptional()
                            .orElseThrow(NoDataFoundException::new)
                            .value1();
                    vo.setId(id);

                    saveRelationalData(null, vo);
                });
                break;
            } catch (org.jooq.exception.DataAccessException e) {
                if (!(e.getCause() instanceof SQLTransientException) || tries == 0) {
                    throw e;
                }
            } catch (TransientDataAccessException e) {
                if (tries == 0) {
                    throw e;
                }
            }
        }

        this.publishEvent(createDaoEvent(DaoEventType.CREATE, vo, null));

        if (this.countMonitor != null)
            this.countMonitor.increment();
    }

    @Override
    public void savePreRelationalData(T existing, T vo) { }

    @Override
    public void saveRelationalData(T existing, T vo) { }

    @Override
    public void update(int id, T vo) {
        update(get(id), vo);
    }

    @Override
    public void update(T existing, T vo) {
        int tries = transactionRetries;
        while (tries-- > 0) {
            try {
                doInTransaction(status -> {
                    savePreRelationalData(existing, vo);

                    create.update(table).set(toRecord(vo))
                            .where(getIdField().eq(vo.getId()))
                            .execute();

                    saveRelationalData(existing, vo);
                });
                break;
            } catch (org.jooq.exception.DataAccessException e) {
                if (!(e.getCause() instanceof SQLTransientException) || tries == 0) {
                    throw e;
                }
            } catch (TransientDataAccessException e) {
                if (tries == 0) {
                    throw e;
                }
            }
        }

        this.publishEvent(createDaoEvent(DaoEventType.UPDATE, vo, existing));
    }

    @Override
    public T get(int id) {
        return getJoinedSelectQuery()
                .where(getIdField().eq(id))
                .limit(1)
                .fetchOne(this::mapRecordLoadRelationalData);
    }

    @Override
    public void getAll(Consumer<T> callback) {
        customizedQuery(getJoinedSelectQuery(), callback);
    }

    @Override
    public List<T> getAll() {
        List<T> items = new ArrayList<>();
        getAll(items::add);
        return items;
    }

    @Override
    public SelectJoinStep<Record> getSelectQuery(List<Field<?>> fields) {
        return create.select(fields)
                .from(table);
    }

    @Override
    public SelectJoinStep<Record> getJoinedSelectQuery() {
        SelectJoinStep<Record> query = getSelectQuery(getSelectFields());
        return joinTables(query, null);
    }

    @Override
    public void loadRelationalData(T vo) { }

    @Override
    public int count() {
        return getCountQuery().from(table)
                .fetchSingle()
                .value1();
    }

    /**
     * Get the base Count query
     * @return
     */
    @Override
    public SelectSelectStep<Record1<Integer>> getCountQuery() {
        Field<Integer> idField = getIdField();
        if (idField == null) {
            return create.selectCount();
        } else {
            return create.select(DSL.count(idField));
        }
    }

    public AtomicIntegerMonitor getCountMonitor(){
        return this.countMonitor;
    }

    /**
     * Get the select columns, override as necessary
     * @return
     */
    public List<Field<?>> getSelectFields() {
        return Arrays.stream(table.fields()).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Add any joins including those in the conditions
     *  Override as necessary
     * @param select
     * @return
     */
    @Override
    public <R extends Record> SelectJoinStep<R> joinTables(SelectJoinStep<R> select, ConditionSortLimit conditions) {
        return select;
    }

    /**
     * Join on permission read conditions to limit results to what the user can 'see'
     */
    @Override
    public <R extends Record> SelectJoinStep<R> joinPermissions(SelectJoinStep<R> select, PermissionHolder user) {
        Field<Integer> readPermissionField = getReadPermissionField();
        return joinPermissionsOnField(select, user, readPermissionField);
    }

    protected <R extends Record> SelectJoinStep<R> joinPermissionsOnField(SelectJoinStep<R> select, PermissionHolder user, Field<Integer> permissionIdField) {
        if (permissionIdField == null || permissionService.hasAdminRole(user)) {
            return select;
        }

        List<Integer> roleIds = permissionService.getAllInheritedRoles(user).stream().map(Role::getId).collect(Collectors.toList());
        Condition roleIdsIn = MintermsRoles.MINTERMS_ROLES.roleId.in(roleIds);

        Table<?> mintermsGranted = this.create.select(MintermsRoles.MINTERMS_ROLES.mintermId)
                .from(MintermsRoles.MINTERMS_ROLES)
                .groupBy(MintermsRoles.MINTERMS_ROLES.mintermId)
                .having(DSL.count().eq(DSL.count(
                        DSL.case_().when(roleIdsIn, DSL.inline(1))
                                .else_(DSL.inline((Integer) null))))).asTable("mintermsGranted");

        Table<?> permissionsGranted = this.create.selectDistinct(PermissionsMinterms.PERMISSIONS_MINTERMS.permissionId)
                .from(PermissionsMinterms.PERMISSIONS_MINTERMS)
                .join(mintermsGranted)
                .on(mintermsGranted.field(MintermsRoles.MINTERMS_ROLES.mintermId).eq(PermissionsMinterms.PERMISSIONS_MINTERMS.mintermId))
                .asTable("permissionsGranted");

        return select.join(permissionsGranted)
                .on(permissionsGranted.field(PermissionsMinterms.PERMISSIONS_MINTERMS.permissionId).in(permissionIdField));
    }

    @Override
    public Field<Integer> getReadPermissionField() {
        Field<?> field = table.field("readPermissionId");
        if (field != null) {
            if (field.getDataType().isNumeric()) {
                return field.coerce(Integer.class);
            }
        }
        return null;
    }

    @Override
    public int customizedCount(ConditionSortLimit conditions, PermissionHolder user) {
        SelectSelectStep<Record1<Integer>> count = getCountQuery();

        SelectJoinStep<Record1<Integer>> select = count.from(table);
        select = joinTables(select, conditions);
        select = joinPermissions(select, user);
        return customizedCount(select, conditions.getCondition());
    }

    @Override
    public int customizedCount(SelectJoinStep<Record1<Integer>> input, Condition condition) {
        Select<Record1<Integer>> select = input;
        if (condition != null) {
            select = input.where(condition);
        }

        LogStopWatch stopWatch = null;
        if (useMetrics) {
            stopWatch = new LogStopWatch();
        }

        int count = select.fetchSingle().value1();

        if (stopWatch != null) {
            Select<Record1<Integer>> selectOutput = select;
            stopWatch.stop(() -> "customizedCount(): " + create.renderInlined(selectOutput), metricsThreshold);
        }

        return count;
    }

    @Override
    public void customizedQuery(ConditionSortLimit conditions, PermissionHolder user, Consumer<T> callback) {
        SelectJoinStep<Record> select = getSelectQuery(getSelectFields());
        select = joinTables(select, conditions);
        select = joinPermissions(select, user);
        customizedQuery(select, conditions.getCondition(), conditions.getSort(), conditions.getLimit(), conditions.getOffset(), callback);
    }

    @Override
    public void customizedQuery(SelectJoinStep<Record> select, Condition condition, List<SortField<?>> sort, Integer limit, Integer offset,
            Consumer<T> callback) {

        SelectConnectByStep<Record> afterWhere = condition == null ? select : select.where(condition);
        SelectLimitStep<Record> afterSort = sort == null ? afterWhere : afterWhere.orderBy(sort);
        Select<Record> offsetStep = afterSort;
        if (limit != null) {
            if (offset != null) {
                offsetStep = afterSort.limit(offset, limit);
            } else {
                offsetStep = afterSort.limit(limit);
            }
        }
        customizedQuery(offsetStep, callback);
    }

    @Override
    public void customizedQuery(Select<Record> select, Consumer<T> callback) {
        LogStopWatch stopWatch = null;
        if (useMetrics) {
            stopWatch = new LogStopWatch();
        }
        try (Stream<Record> stream = select.stream()) {
            stream.map(this::mapRecordLoadRelationalData)
                    .filter(Objects::nonNull)
                    .forEach(callback);
        }finally {
            if (stopWatch != null) {
                stopWatch.stop(() -> "customizedQuery(): " + create.renderInlined(select), metricsThreshold);
            }
        }
    }

    @Override
    public <TYPE> TYPE customizedQuery(Select<Record> select, ResultSetExtractor<TYPE> callback) {
        LogStopWatch stopWatch = null;
        if (useMetrics) {
            stopWatch = new LogStopWatch();
        }
        try {
            try (ResultSet resultSet = select.fetchResultSet()) {
                return callback.extractData(resultSet);
            } catch (SQLException e) {
                throw new DataAccessException("Error extracting data from result set", e);
            }
        }finally {
            if (stopWatch != null) {
                stopWatch.stop(() -> "customizedQuery(): " + create.renderInlined(select), metricsThreshold);
            }
        }
    }

    /**
     * @return Map of RQL property name to SQL fields
     */
    protected Map<String, Field<?>> createFieldMap() {
        return Arrays.stream(table.fields())
                .collect(Collectors.toMap(Field::getName, Function.identity(), (a,b) -> a, HashMap::new));
    }

    protected Map<String, RQLSubSelectCondition> createSubSelectMap() {
        return new HashMap<>();
    }

    protected Map<String, Function<Object, Object>> createValueConverterMap() {
        return table.fieldStream().filter(f -> f.getDataType().getSQLDataType() == SQLDataType.CHAR)
                .collect(Collectors.toMap(Field::getName, e -> RQLToCondition.BOOLEAN_VALUE_CONVERTER, (a,b) -> a, HashMap::new));
    }

    @Override
    public ConditionSortLimit rqlToCondition(ASTNode rql, Map<String, RQLSubSelectCondition> subSelectMapping, Map<String, Field<?>> fieldMap, Map<String, Function<Object, Object>> valueConverters) {
        RQLToCondition rqlToCondition = createRqlToCondition(combine(this.subSelectMap, subSelectMapping), combine(this.fieldMap, fieldMap), combine(this.valueConverterMap, valueConverters));
        return rqlToCondition.visit(rql);
    }

    protected <X, Y> Map<X,Y> combine(Map<? extends X,? extends Y> a, Map<? extends X,? extends Y> b) {
        if (b == null || b.isEmpty()) {
            return unmodifiableMap(a);
        }
        HashMap<X,Y> result = new HashMap<>(a);
        result.putAll(b);
        return unmodifiableMap(result);
    }

    protected <X, Y> Map<X,Y> unmodifiableMap(Map<? extends X,? extends Y> delegate) {
        if (delegate.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(delegate);
    }

    /**
     * Create a stateful rql to condition (Override as necessary)
     * @param subSelectMapping not null
     * @param fieldMap not null
     * @param converterMap not null
     * @return
     */
    protected RQLToCondition createRqlToCondition(Map<String, RQLSubSelectCondition> subSelectMapping, Map<String, Field<?>> fieldMap, Map<String, Function<Object, Object>> converterMap) {
        return new RQLToCondition(subSelectMapping, fieldMap, converterMap);
    }

    protected DaoEvent<T> createDaoEvent(DaoEventType type, T vo, T existing) {
        if (type == DaoEventType.UPDATE) {
            return new DaoEvent<T>(this, type, vo, existing);
        } else {
            return new DaoEvent<T>(this, type, vo);
        }
    }

    protected void publishEvent(DaoEvent<T> event) {
        if (this.eventPublisher != null) {
            this.eventPublisher.publishEvent(event);
        }
    }

    /**
     * Get a writer for serializing JSON
     * @return
     */
    public ObjectWriter getObjectWriter(Class<?> type) {
        return mapper.writerFor(type);
    }

    /**
     * Get a reader for use de-serializing JSON
     * @return
     */
    public ObjectReader getObjectReader(Class<?> type) {
        return mapper.readerFor(type);
    }

    @Override
    public void lockRow(int id) {
        create.select().from(table)
        .where(getIdField().eq(id))
        .forUpdate()
        .fetch();
    }

    /**
     * Helper to convert JSON Node for db
     * @param data
     * @return
     */
    protected String convertData(JsonNode data) {
        try {
            if(data == null) {
                return null;
            }else {
                return getObjectWriter(JsonNode.class).writeValueAsString(data);
            }
        }catch(JsonProcessingException e) {
            LOG.error(e.getMessage(), e);
        }
        return null;
    }

    protected JsonNode extractData(Clob c) throws SQLException {
        if (c != null) {
            try (Reader reader = c.getCharacterStream()) {
                return getObjectReader(JsonNode.class).readValue(reader);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return null;
    }

    protected JsonNode extractData(String c) {
        if (c != null) {
            try {
                return getObjectReader(JsonNode.class).readValue(c);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return null;
    }

    /**
     * Work around jOOQ bug https://github.com/jOOQ/jOOQ/issues/11148
     * Getting a String field from a record is throwing ClassCastException on MySQL as it returns a JSON instance.
     */
    protected JsonNode extractDataFromObject(Object c) {
        if (c == null) {
            return null;
        } if (c instanceof JSON) {
            return extractData(((JSON) c).data());
        } else if (c instanceof String) {
            return extractData((String) c);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public int count(PermissionHolder user) {
        return customizedCount(new ConditionSortLimit(null, null, null, null), user);
    }

    @Override
    public int count(PermissionHolder user, String rql) {
        ConditionSortLimit csl = rqlToCondition(RQLUtils.parseRQLtoAST(rql), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        return customizedCount(csl, user);
    }

    @Override
    public List<T> list(PermissionHolder user) {
        List<T> list = new ArrayList<>();
        list(user, list::add);
        return list;
    }

    @Override
    public void list(PermissionHolder user, Consumer<T> consumer) {
        customizedQuery(new ConditionSortLimit(null, null, null, null), user, consumer);
    }

    @Override
    public List<T> query(PermissionHolder user, String rql) {
        List<T> list = new ArrayList<>();
        query(user, rql, list::add);
        return list;
    }

    @Override
    public void query(PermissionHolder user, String rql, Consumer<T> consumer) {
        ConditionSortLimit csl = rqlToCondition(RQLUtils.parseRQLtoAST(rql), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        customizedQuery(csl, user, consumer);
    }

    @Override
    public QueryBuilder<T> buildQuery(PermissionHolder user) {
        return new QueryBuilder<T>(fieldMap, valueConverterMap, csl -> customizedCount(csl, user), (csl, consumer) -> customizedQuery(csl, user, consumer));
    }

}
