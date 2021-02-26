/*
 * Copyright (C) 2020 Infinite Automation Software. All rights reserved.
 */
package com.serotonin.m2m2.db.dao;

import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.default_;
import static org.jooq.impl.DSL.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectSeekStep2;
import org.jooq.impl.DSL;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.infiniteautomation.mango.db.tables.Minterms;
import com.infiniteautomation.mango.db.tables.MintermsRoles;
import com.infiniteautomation.mango.db.tables.Permissions;
import com.infiniteautomation.mango.db.tables.PermissionsMinterms;
import com.infiniteautomation.mango.db.tables.Roles;
import com.infiniteautomation.mango.permission.MangoPermission;
import com.serotonin.m2m2.vo.role.Role;

/**
 * NOTE: Permissions are cached, usage of tjos dao should be limited to within the PermissionService
 * @author Jared Wiltshire
 */
@Repository
public class PermissionDao extends BaseDao {

    private final Roles roleTable = Roles.ROLES;
    private final Minterms minterms = Minterms.MINTERMS;
    private final MintermsRoles mintermsRoles = MintermsRoles.MINTERMS_ROLES;
    private final Permissions permissions = Permissions.PERMISSIONS;
    private final PermissionsMinterms permissionsMinterms = PermissionsMinterms.PERMISSIONS_MINTERMS;

    /**
     * Get a MangoPermission by id
     * @param id
     * @return permission if found or null
     */
    public MangoPermission get(Integer id) {
        //TODO Mango 4.0 improve performance
        //Fist check to see if it exists as it may have no minterms
        Integer foundId = create.select(permissions.id).from(permissions).where(permissions.id.equal(id)).fetchOneInto(Integer.class);
        if(foundId == null) {
            return null;
        }

        List<Field<?>> fields = new ArrayList<>();
        fields.add(roleTable.id);
        fields.add(roleTable.xid);
        fields.add(permissionsMinterms.mintermId);

        SelectSeekStep2<Record, Integer, Integer> select = create.select(fields).from(permissionsMinterms)
                .join(mintermsRoles).on(permissionsMinterms.mintermId.eq(mintermsRoles.mintermId))
                .join(roleTable).on(roleTable.id.eq(mintermsRoles.roleId))
                .where(permissionsMinterms.permissionId.eq(id))
                .orderBy(permissionsMinterms.permissionId.asc(), permissionsMinterms.mintermId.asc());

        String sql = select.getSQL();
        List<Object> arguments = select.getBindValues();
        Object[] argumentsArray = arguments.toArray(new Object[arguments.size()]);

        return this.query(sql, argumentsArray, new ResultSetExtractor<MangoPermission>() {

            private int roleIdIndex = 1;
            private int roleXidIndex = 2;
            private int minterIdIndex = 3;
            @Override
            public MangoPermission extractData(ResultSet rs)
                    throws SQLException, DataAccessException {

                if(rs.next()) {
                    Set<Set<Role>> roleSet = new HashSet<>();
                    Set<Role> minTerm = new HashSet<>();
                    roleSet.add(minTerm);
                    minTerm.add(new Role(rs.getInt(roleIdIndex), rs.getString(roleXidIndex)));

                    int mintermId = rs.getInt(minterIdIndex);
                    while(rs.next()) {
                        if(rs.getInt(minterIdIndex) == mintermId) {
                            //Add to current minterm
                            minTerm.add(new Role(rs.getInt(roleIdIndex), rs.getString(roleXidIndex)));
                        }else {
                            //Add to next minterm
                            minTerm = new HashSet<>();
                            roleSet.add(minTerm);
                            minTerm.add(new Role(rs.getInt(roleIdIndex), rs.getString(roleXidIndex)));
                            mintermId = rs.getInt(minterIdIndex);
                        }
                    }
                    MangoPermission permission = new MangoPermission(id, roleSet);
                    return permission;
                }else {
                    return new MangoPermission(id);
                }
            }

        });
    }

    /**
     * Find the Permission id of the combination of these minterms or create one that matches
     *
     * @param minterms
     * @return
     */
    public Integer permissionId(Set<Set<Role>> minterms) {
        // We need to always do this in a new transaction as this ends up in the cache. Otherwise if we are in a
        // nested transaction the permission ID will end up in the cache before the transaction is committed. The
        // caveats of this are:
        // a) if the outer transaction is rolled back we may be left with a dangling permission that is not used
        // b) the permission may get deleted before the outer transaction is committed
        TransactionTemplate txTemplate = new TransactionTemplate(getTransactionManager(),
                new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        return txTemplate.execute(txStatus -> getOrInsertPermission(minterms));
    }

    private Integer getOrInsertPermission(Set<Set<Role>> minterms) {
        //TODO Mango 4.0 Optimize this whole method
        Set<Integer> mintermIds = minterms.stream()
                .map(this::getOrInsertMinterm)
                .collect(Collectors.toSet());

        Integer permissionId;
        if(minterms.isEmpty()) {
            permissionId = create.select(permissions.id).from(permissions)
                    .leftJoin(permissionsMinterms).on(permissions.id.eq(permissionsMinterms.permissionId))
                    .where(permissionsMinterms.permissionId.isNull()).limit(1).fetchOne(0, Integer.class);
        }else {
            permissionId = create.select(permissionsMinterms.permissionId)
                    .from(permissionsMinterms)
                    .groupBy(permissionsMinterms.permissionId)
                    .having(count(when(permissionsMinterms.mintermId.in(mintermIds), 1).else_((Integer) null)).equal(mintermIds.size()),
                            count(permissionsMinterms.permissionId).equal(mintermIds.size()))
                    .limit(1)
                    .fetchOne(0, Integer.class);
        }

        // no matching permission exists already, insert a new one
        if (permissionId == null) {
            permissionId = create.insertInto(permissions, permissions.id)
                    .values(default_(permissions.id))
                    .returningResult(permissions.id)
                    .fetchOne().get(permissions.id);

            int permissionIdFinal = permissionId;
            create.batch(
                    mintermIds.stream()
                    .map(id -> DSL.insertInto(permissionsMinterms)
                            .columns(permissionsMinterms.permissionId, permissionsMinterms.mintermId)
                            .values(permissionIdFinal, id))
                    .collect(Collectors.toList()))
            .execute();
        }

        return permissionId;
    }

    private int getOrInsertMinterm(Set<Role> minterm) {
        if (minterm.isEmpty()) {
            throw new IllegalArgumentException("Minterm should never be empty");
        }

        Set<Integer> roleIds = minterm.stream()
                .map(Role::getId)
                .collect(Collectors.toSet());

        Integer mintermId = create.select(mintermsRoles.mintermId)
                .from(mintermsRoles)
                .groupBy(mintermsRoles.mintermId)
                .having(count(when(mintermsRoles.roleId.in(roleIds), 1).else_((Integer) null)).equal(minterm.size()),
                        count(mintermsRoles.roleId).equal(roleIds.size()))
                .limit(1)
                .fetchOne(0, Integer.class);

        // no matching minterm exists already, insert a new one
        if (mintermId == null) {
            mintermId = create.insertInto(minterms, minterms.id)
                    .values(default_(minterms.id))
                    .returningResult(minterms.id)
                    .fetchOne().get(permissions.id);

            int mintermIdFinal = mintermId;
            create.batch(
                    roleIds.stream()
                    .map(id -> DSL.insertInto(mintermsRoles)
                            .columns(mintermsRoles.mintermId, mintermsRoles.roleId)
                            .values(mintermIdFinal, id))
                    .collect(Collectors.toList()))
            .execute();
        }

        return mintermId;
    }

    public void roleUnlinked() {
        //Clean up minterms that are orphaned (i.e. belong to no role)
        create.deleteFrom(minterms).where(
                minterms.id.in(
                        create.select(minterms.id).from(minterms)
                        .leftJoin(mintermsRoles).on(mintermsRoles.mintermId.eq(minterms.id))
                        .where(mintermsRoles.mintermId.isNull()))).execute();

    }

    /**
     * Clean up references from an unlinked permission
     */
    private void permissionUnlinked() {
        //Clean up minterms that are orphaned (i.e. belong to no permission)
        create.deleteFrom(minterms).where(
                minterms.id.in(
                        create.select(minterms.id).from(minterms)
                        .leftJoin(permissionsMinterms).on(permissionsMinterms.mintermId.eq(minterms.id))
                        .where(permissionsMinterms.permissionId.isNull()))).execute();
    }

    /**
     * Attempt to delete a permission, will not succeed if another object references the permission (via foreign key).
     * @param permissionId
     * @return true if the permission was deleted
     */
    public boolean deletePermission(Integer permissionId) {
        return doInTransaction(txStatus -> {
            try {
                if (create.deleteFrom(permissions).where(permissions.id.eq(permissionId)).execute() > 0) {
                    permissionUnlinked();
                }
            } catch (Exception e) {
                return false;
            }
            return true;
        });
    }
}
