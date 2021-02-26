/**
 * Copyright (C) 2019  Infinite Automation Software. All rights reserved.
 */
package com.serotonin.m2m2.db.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.SelectOnConditionStep;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infiniteautomation.mango.db.query.RQLSubSelectCondition;
import com.infiniteautomation.mango.db.query.RQLToCondition.RQLVisitException;
import com.infiniteautomation.mango.db.tables.RoleInheritance;
import com.infiniteautomation.mango.db.tables.Roles;
import com.infiniteautomation.mango.db.tables.records.RolesRecord;
import com.infiniteautomation.mango.spring.MangoRuntimeContextConfiguration;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.event.type.AuditEventType;
import com.serotonin.m2m2.vo.role.Role;
import com.serotonin.m2m2.vo.role.RoleVO;

/**
 * @author Terry Packer
 *
 */
@Repository
public class RoleDao extends AbstractVoDao<RoleVO, RolesRecord, Roles> {

    private final PermissionDao permissionDao;

    // cannot inject permission service in this DAO or it would introduce a circular dependency
    @Autowired
    private RoleDao(@Qualifier(MangoRuntimeContextConfiguration.DAO_OBJECT_MAPPER_NAME) ObjectMapper mapper,
                    ApplicationEventPublisher publisher,
                    PermissionDao permissionDao) {
        super(AuditEventType.TYPE_ROLE,
                Roles.ROLES,
                new TranslatableMessage("internal.monitor.ROLE_COUNT"),
                mapper, publisher, null);
        this.permissionDao = permissionDao;
    }

    @Override
    protected Map<String, RQLSubSelectCondition> createSubSelectMap() {
        Map<String, RQLSubSelectCondition> subselects = super.createSubSelectMap();
        subselects.put("inherited", createInheritedRoleCondition());
        subselects.put("inheritedBy", createInheritedByRoleCondition());
        return subselects;
    }

    @Override
    public void saveRelationalData(RoleVO existing, RoleVO vo) {
        if(existing != null) {
            //Drop the mappings

            this.create.deleteFrom(RoleInheritance.ROLE_INHERITANCE).where(RoleInheritance.ROLE_INHERITANCE.roleId.eq(vo.getId())).execute();
        }

        if(vo.getInherited() != null && vo.getInherited().size() > 0) {
            List<Query> inserts = new ArrayList<>();
            for(Role role : vo.getInherited()) {
                inserts.add(DSL.insertInto(RoleInheritance.ROLE_INHERITANCE).columns(
                        RoleInheritance.ROLE_INHERITANCE.roleId, RoleInheritance.ROLE_INHERITANCE.inheritedRoleId)
                        .values(vo.getId(), role.getId()));
            }
            create.batch(inserts).execute();
        }
    }

    @Override
    public void loadRelationalData(RoleVO vo) {
        Set<Role> inherited = getInherited(vo.getId());
        vo.setInherited(inherited);
    }

    @Override
    public void deletePostRelationalData(RoleVO vo) {
        permissionDao.roleUnlinked();
    }

    @Override
    protected String getXidPrefix() {
        return RoleVO.XID_PREFIX;
    }

    @Override
    protected Record toRecord(RoleVO vo) {
        Record record = table.newRecord();
        record.set(table.xid, vo.getXid());
        record.set(table.name, vo.getName());
        return record;
    }

    @Override
    public RoleVO mapRecord(Record record) {
        return new RoleVO(record.get(table.id), record.get(table.xid), record.get(table.name));
    }

    public Role mapRecordToRole(Record record) {
        return new Role(record.get(table.id), record.get(table.xid));
    }

    /**
     * Get all roles that inherit this role
     * @param role
     * @return
     */
    public Set<Role> getRolesThatInherit(Role role) {
        return this.doInTransaction((tx) -> {
            Set<Role> all = new HashSet<Role>();
            addRolesThatInherit(role, all);
            return all;
        });
    }

    /**
     * Recursively add all roles that inherit this role
     * @param role
     * @param all
     */
    private void addRolesThatInherit(Role role, Set<Role> all) {
        Set<Role> inherited = getRolesThatInherit(role.getId());
        for(Role inheritedRole : inherited) {
            all.add(inheritedRole);
            addRolesThatInherit(inheritedRole, all);
        }
    }

    /**
     * Get the roles that inherit this role, one level deep.
     * @param roleId
     * @return
     */
    private Set<Role> getRolesThatInherit(int roleId) {
        try (Stream<Record> stream = this.create.select(getSelectFields())
                .from(table)
                .join(RoleInheritance.ROLE_INHERITANCE)
                .on(getIdField().eq(RoleInheritance.ROLE_INHERITANCE.roleId))
                .where(RoleInheritance.ROLE_INHERITANCE.inheritedRoleId.eq(roleId))
                .stream()) {

            return Collections.unmodifiableSet(stream.map(this::mapRecordToRole).collect(Collectors.toSet()));
        }
    }

    /**
     * Recursively get a set of all inherited roles of this role
     * @param role
     * @return
     */
    public Set<Role> getFlatInheritance(Role role) {
        return this.doInTransaction((tx) -> {
            Set<Role> all = new HashSet<Role>();
            addInheritance(role, all);
            return all;
        });
    }

    /**
     * Recursively add all inherited roles
     * @param role
     * @param all
     */
    private void addInheritance(Role role, Set<Role> all) {
        Set<Role> inherited = getInherited(role.getId());
        for(Role inheritedRole : inherited) {
            all.add(inheritedRole);
            addInheritance(inheritedRole, all);
        }
    }

    /**
     * Get the inherited roles of this role from the database,
     *  one level deep only.
     * @param roleId
     * @return
     */
    private Set<Role> getInherited(int roleId) {
        try (Stream<Record> stream = this.create.select(getSelectFields())
                .from(table)
                .join(RoleInheritance.ROLE_INHERITANCE)
                .on(getIdField().eq(RoleInheritance.ROLE_INHERITANCE.inheritedRoleId))
                .where(RoleInheritance.ROLE_INHERITANCE.roleId.eq(roleId))
                .stream()) {

            return Collections.unmodifiableSet(stream.map(this::mapRecordToRole).collect(Collectors.toSet()));
        }
    }

    public RQLSubSelectCondition createInheritedRoleCondition() {
        return (operation, node) -> {
            List<Object> arguments = node.getArguments();

            //Check the role Xid input
            if (arguments.size() > 2) {
                throw new RQLVisitException(String.format("Only single arguments supported for node type '%s'", node.getName()));
            }

            Object roleXid = arguments.get(1);
            Integer roleId = null;

            SelectConditionStep<Record1<Integer>> afterWhere;
            //TODO Mango 4.0 Should really used role cache in PermissionService but there is a
            // circular dependency if injected due to our use of the RoleDao
            if(roleXid != null) {
                RoleVO role = getByXid((String)roleXid);
                if(role != null) {
                    roleId = role.getId();
                }
                SelectJoinStep<Record1<Integer>> select = create.select(RoleInheritance.ROLE_INHERITANCE.roleId)
                        .from(RoleInheritance.ROLE_INHERITANCE);
                afterWhere = select.where(RoleInheritance.ROLE_INHERITANCE.inheritedRoleId.eq(roleId));
            }else {
                //Find all roles with no inherited roles
                SelectJoinStep<Record1<Integer>> select = create.select(getIdField()).from(table);
                SelectOnConditionStep<Record1<Integer>> afterJoin = select.leftJoin(RoleInheritance.ROLE_INHERITANCE)
                        .on(RoleInheritance.ROLE_INHERITANCE.roleId.eq(getIdField()));
                afterWhere = afterJoin.where(RoleInheritance.ROLE_INHERITANCE.roleId.isNull());
            }

            switch(operation) {
                case CONTAINS:
                    return getIdField().in(afterWhere.asField());
                default:
                    throw new RQLVisitException(String.format("Unsupported node type '%s' for property '%s'", node.getName(), arguments.get(0)));
            }
        };
    }

    public RQLSubSelectCondition createInheritedByRoleCondition() {
        return (operation, node) -> {
            List<Object> arguments = node.getArguments();

            //Check the role Xid input
            if (arguments.size() > 2) {
                throw new RQLVisitException(String.format("Only single arguments supported for node type '%s'", node.getName()));
            }

            Object roleXid = arguments.get(1);
            Integer roleId = null;

            SelectConditionStep<Record1<Integer>> afterWhere;
            //TODO Mango 4.0 Should really used role cache in PermissionService but there is a
            // circular dependency if injected due to our use of the RoleDao
            if (roleXid != null) {
                //Find all roles inherited by this role
                RoleVO role = getByXid((String) roleXid);
                if (role != null) {
                    roleId = role.getId();
                }
                SelectJoinStep<Record1<Integer>> select = create.select(RoleInheritance.ROLE_INHERITANCE.inheritedRoleId)
                        .from(RoleInheritance.ROLE_INHERITANCE);
                afterWhere = select.where(RoleInheritance.ROLE_INHERITANCE.roleId.eq(roleId));
            } else {
                //Find all roles with that are not inherited by any role
                SelectJoinStep<Record1<Integer>> select = create.select(getIdField()).from(table);
                SelectOnConditionStep<Record1<Integer>> afterJoin = select.leftJoin(RoleInheritance.ROLE_INHERITANCE)
                        .on(RoleInheritance.ROLE_INHERITANCE.inheritedRoleId.eq(getIdField()));
                afterWhere = afterJoin.where(RoleInheritance.ROLE_INHERITANCE.inheritedRoleId.isNull());
            }

            switch (operation) {
                case CONTAINS:
                    return getIdField().in(afterWhere.asField());
                default:
                    throw new RQLVisitException(String.format("Unsupported node type '%s' for property '%s'", node.getName(), arguments.get(0)));
            }
        };
    }
}
