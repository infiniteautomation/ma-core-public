/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */
package com.serotonin.m2m2.db.upgrade;

import java.util.Map;

import org.jooq.impl.DSL;
import org.springframework.transaction.support.TransactionTemplate;

import com.infiniteautomation.mango.db.tables.Permissions;
import com.infiniteautomation.mango.db.tables.Users;
import com.infiniteautomation.mango.permission.MangoPermission;
import com.serotonin.db.spring.ExtendedJdbcTemplate;
import com.serotonin.m2m2.vo.role.Role;

/**
 * Adds the oAuth2Users table
 *
 * @author Jared Wiltshire
 */
public class Upgrade37 extends DBUpgrade implements PermissionMigration {

    @Override
    protected void upgrade() throws Exception {
        Users users = Users.USERS;
        Permissions permissions = Permissions.PERMISSIONS;

        doInTransaction(txStatus -> {
            MangoPermission adminOnlyPermission = getOrCreatePermissionNoCache(MangoPermission.superadminOnly());
            create.batch(
                    DSL.alterTable(users).addColumn(users.readPermissionId),
                    DSL.alterTable(users).addColumn(users.editPermissionId),
                    DSL.update(users).set(users.readPermissionId, adminOnlyPermission.getId()),
                    DSL.update(users).set(users.editPermissionId, adminOnlyPermission.getId()),
                    DSL.alterTable(users).add(DSL.constraint("usersFk1")
                            .foreignKey(users.readPermissionId)
                            .references(permissions, permissions.id)
                            .onDeleteRestrict()),
                    DSL.alterTable(users).add(DSL.constraint("usersFk2")
                            .foreignKey(users.editPermissionId)
                            .references(permissions, permissions.id)
                            .onDeleteRestrict())
            ).execute();
        });
    }

    @Override
    protected String getNewSchemaVersion() {
        return "38";
    }

    @Override
    public ExtendedJdbcTemplate getJdbcTemplate() {
        return ejt;
    }

    @Override
    public Map<MangoPermission, MangoPermission> permissionCache() {
        // not used
        return null;
    }

    @Override
    public Map<Role, Role> roleCache() {
        // not used
        return null;
    }

    @Override
    public TransactionTemplate getTransactionTemplate() {
        return super.getTransactionTemplate();
    }
}
