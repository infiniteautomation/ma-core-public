/**
 * Copyright (C) 2019  Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.mango.spring.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import com.infiniteautomation.mango.db.tables.Users;
import com.infiniteautomation.mango.db.tables.records.UsersRecord;
import com.infiniteautomation.mango.permission.MangoPermission;
import com.infiniteautomation.mango.util.exception.NotFoundException;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.db.dao.UserDao;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.module.PermissionDefinition;
import com.serotonin.m2m2.module.definitions.permissions.ChangeOwnUsernamePermissionDefinition;
import com.serotonin.m2m2.module.definitions.permissions.UserCreatePermission;
import com.serotonin.m2m2.module.definitions.permissions.UserEditSelfPermission;
import com.serotonin.m2m2.vo.User;
import com.serotonin.m2m2.vo.permission.PermissionException;
import com.serotonin.m2m2.vo.permission.PermissionHolder;
import com.serotonin.m2m2.vo.role.Role;

/**
 * @author Terry Packer
 *
 */
public class UsersServiceTest extends AbstractVOServiceWithPermissionsTest<User, UsersRecord, Users, UserDao, UsersService> {

    public UsersServiceTest() {
    }

    @Test
    @Override
    public void testUpdateViaXid() {
        //We don't have an xid
    }

    @Test()
    @Override
    public void testDeleteViaXid() {
        //We don't have an xid
    }

    /**
     * Test that we can read ourself
     */
    @Test
    @Override
    public void testUserReadRole() {
        runTest(() -> {
            User vo = newVO(editUser);
            User saved = service.insert(vo);
            runAs.runAs(saved, () -> {
                User fromDb = service.get(saved.getId());
                assertVoEqual(saved, fromDb);
            });
        });
    }

    @Test
    @Override
    public void testCreatePrivilegeSuccess() {
        runTest(() -> {
            User vo = newVO(editUser);
            addRoleToCreatePermission(editRole);
            vo.setRoles(Collections.singleton(editRole));
            runAs.runAs(editUser, () -> {
                service.insert(vo);
            });
        });
    }

    /**
     * Test edit self permission
     */
    @Override
    @Test
    public void testUserEditRole() {
        runTest(() -> {
            addRoleToEditSelfPermission(readRole);
            User vo = newVO(editUser);
            User saved = service.insert(vo);
            runAs.runAs(vo, () -> {
                saved.setName("I edited myself");
                service.update(saved.getUsername(), saved);
                User updated = service.get(vo.getId());
                assertVoEqual(saved, updated);
            });
        });
    }

    @Test
    public void testUserEditRoleDefaultUser() {
        runTest(() -> {
            User vo = newVO(editUser);
            User saved = service.insert(vo);

            //Ensure the ability to edit self
            List<Role> myRoles = saved.getRoles().stream().collect(Collectors.toList());
            addRoleToEditSelfPermission(myRoles.get(0));

            runAs.runAs(saved, () -> {
                saved.setName("I edited myself");
                service.update(saved.getUsername(), saved);
                User updated = service.get(saved.getId());
                assertVoEqual(saved, updated);
            });
        });
    }

    @Override
    @Test(expected = PermissionException.class)
    public void testUserEditRoleFails() {
        runTest(() -> {
            addRoleToEditSelfPermission(editRole);
            removeRoleFromEditSelfPermission(PermissionHolder.USER_ROLE);
            runAs.runAs(readUser, () -> {
                User toUpdate = service.get(readUser.getId());
                toUpdate.setName("I edited myself");
                toUpdate.setPassword("");
                service.update(toUpdate.getUsername(), toUpdate);
                User updated = service.get(readUser.getId());
                assertVoEqual(toUpdate, updated);
            });
        });
    }

    @Test
    @Override
    public void testCannotRemoveEditAccess() {
        //skipped as not possible
    }

    @Test
    @Override
    public void testAddReadRoleUserDoesNotHave() {
        //cannot edit another user as non-admin so skipped
    }

    @Test
    @Override
    public void testReadRolesCannotBeNull() {
        //skipped as no read roles on a user
    }

    @Test
    @Override
    public void testCannotRemoveReadAccess() {
        //skipped as no read roles on a user
    }

    @Test
    @Override
    public void testEditRolesCannotBeNull() {
        //skipped as no edit roles
    }

    @Test
    @Override
    public void testCountQueryReadPermissionEnforcement() {
        //Skipped as we don't filter in DB (yet)
    }

    @Test
    @Override
    public void testCountQueryEditPermissionEnforcement() {
        //Skipped as we don't filter in DB (yet)
    }

    @Test
    @Override
    public void testQueryReadPermissionEnforcement() {
        //Skipped as we don't filter in DB (yet)
    }

    @Test
    @Override
    public void testQueryEditPermissionEnforcement() {
        //Skipped as we don't filter in DB (yet)
    }

    @Test
    @Override
    public void testAddEditRoleUserDoesNotHave() {
        runTest(() -> {
            User vo = newVO(readUser);
            vo.setRoles(Collections.singleton(readRole));
            User saved = service.insert(vo);

            //Ensure the ability to edit self
            List<Role> myRoles = new ArrayList<>(saved.getRoles());
            addRoleToEditSelfPermission(myRoles.get(0));

            runAs.runAs(saved, () -> {
                myRoles.add(editRole);
                saved.setRoles(new HashSet<>(myRoles));
                service.update(saved.getUsername(), saved);
            });
        }, "roles");
    }

    @Test
    @Override
    public void testDeleteRoleUpdateVO() {
        runTest(() -> {
            User vo = newVO(readUser);
            vo.setRoles(Collections.singleton(readRole));
            service.insert(vo);
            User fromDb = service.get(vo.getId());
            assertVoEqual(vo, fromDb);
            roleService.delete(readRole.getId());
            //Remove the read role from the local copy
            Set<Role> roles = new HashSet<>(fromDb.getRoles());
            roles.remove(readRole);
            fromDb.setRoles(roles);
            //Check database
            User updated = service.get(fromDb.getId());
            assertVoEqual(fromDb, updated);
            //Check cache
            updated = service.get(fromDb.getUsername());
            assertVoEqual(fromDb, updated);
        });
    }

    @Test(expected = NotFoundException.class)
    @Override
    public void testDelete() {
        runTest(() -> {
            User vo = insertNewVO(readUser);
            vo.setRoles(Collections.singleton(readRole));
            service.update(vo.getUsername(), vo);
            User fromDb = service.get(vo.getId());
            assertVoEqual(vo, fromDb);
            service.delete(vo.getId());

            //Ensure the mappings are gone
            assertEquals(0, getDao().getUserRoles(vo).size());

            service.get(vo.getId());
        });
    }

    @Test
    public void testRemoveRolesFails() {
        runTest(() -> {
            User vo = newVO(readUser);

            vo.setRoles(Collections.singleton(readRole));
            service.insert(vo);
            User saved = service.get(vo.getId());
            runAs.runAs(saved, () -> {
                saved.setRoles(Collections.singleton(PermissionHolder.USER_ROLE));
                service.update(saved.getUsername(), saved);
            });
        }, "roles");
    }

    @Test
    public void testChangeUsernameAsAdmin() {
        User vo = newVO(readUser);
        service.insert(vo);
        vo = service.get(vo.getId());
        vo.setUsername(UUID.randomUUID().toString());
        service.update(vo.getId(), vo);
    }

    @Test
    public void testChangeUsernameWithoutPermission() {
        runTest(() -> {
            removeRoleFromEditSelfPermission(PermissionHolder.USER_ROLE);
            addRoleToEditSelfPermission(readRole);
            PermissionDefinition def = ModuleRegistry.getPermissionDefinition(ChangeOwnUsernamePermissionDefinition.PERMISSION);

            Set<Set<Role>> roleSet = def.getPermission().getRoles();
            Set<Set<Role>> newRoles = new HashSet<>();
            newRoles.add(Collections.singleton(editRole));
            for (Set<Role> roles : roleSet) {
                if (roles.contains(PermissionHolder.USER_ROLE)) {
                    continue; //skip the user role
                }
                newRoles.add(roles);
            }
            Common.getBean(SystemPermissionService.class).update(new MangoPermission(newRoles), def);

            User vo = newVO(readUser);
            vo.setRoles(Collections.singleton(readRole));
            service.insert(vo);
            User saved = service.get(vo.getId());

            runAs.runAs(saved, () -> {
                saved.setUsername(UUID.randomUUID().toString());
                service.update(saved.getId(), saved);
            });
        }, "username");
    }

    public void testChangeUsernameWithPermission() {

        //Add read role to change username permission
        PermissionDefinition def = ModuleRegistry.getPermissionDefinition(ChangeOwnUsernamePermissionDefinition.PERMISSION);
        Set<Set<Role>> roleSet = def.getPermission().getRoles();
        Set<Set<Role>> newRoles = new HashSet<>();
        newRoles.add(Collections.singleton(readRole));
        for (Set<Role> roles : roleSet) {
            newRoles.add(new HashSet<>(roles));
        }
        Common.getBean(SystemPermissionService.class).update(new MangoPermission(newRoles), def);

        //Ensure they can edit self
        addRoleToEditSelfPermission(readRole);

        User vo = newVO(readUser);
        vo.setRoles(Collections.singleton(readRole));
        service.insert(vo);
        User saved = service.get(vo.getId());

        runAs.runAs(saved, () -> {
            saved.setUsername(UUID.randomUUID().toString());
            service.update(saved.getId(), saved);
        });
    }

    void addRoleToEditSelfPermission(Role vo) {
        PermissionDefinition def = ModuleRegistry.getPermissionDefinition(UserEditSelfPermission.PERMISSION);
        Set<Set<Role>> roleSet = def.getPermission().getRoles();
        Set<Set<Role>> newRoles = new HashSet<>();
        newRoles.add(Collections.singleton(vo));
        for (Set<Role> roles : roleSet) {
            newRoles.add(new HashSet<>(roles));
        }
        Common.getBean(SystemPermissionService.class).update(new MangoPermission(newRoles), def);
    }

    void removeRoleFromEditSelfPermission(Role vo) {
        PermissionDefinition def = ModuleRegistry.getPermissionDefinition(UserEditSelfPermission.PERMISSION);
        MangoPermission permission = def.getPermission();
        Common.getBean(SystemPermissionService.class).update(new MangoPermission(permission.withoutRole(vo).getRoles()), def);
    }

    @Override
    String getCreatePermissionType() {
        return UserCreatePermission.PERMISSION;
    }

    @Override
    void setReadPermission(MangoPermission permission, User vo) {

    }

    @Override
    void setEditPermission(MangoPermission permission, User vo) {

    }

    @Override
    UsersService getService() {
        return Common.getBean(UsersService.class);
    }

    @Override
    UserDao getDao() {
        return UserDao.getInstance();
    }

    @Override
    void assertVoEqual(User expected, User actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getUsername(), actual.getUsername());
        assertEquals(expected.getPassword(), actual.getPassword());
        assertEquals(expected.getEmail(), actual.getEmail());
        assertEquals(expected.getPhone(), actual.getPhone());
        assertEquals(expected.isDisabled(), actual.isDisabled());
        assertEquals(expected.getHomeUrl(), actual.getHomeUrl());
        assertEquals(expected.getLastLogin(), actual.getLastLogin());
        assertEquals(expected.getReceiveAlarmEmails(), actual.getReceiveAlarmEmails());
        assertEquals(expected.isReceiveOwnAuditEvents(), actual.isReceiveOwnAuditEvents());

        assertEquals(expected.getTimezone(), actual.getTimezone());
        assertEquals(expected.isMuted(), actual.isMuted());
        assertEquals(expected.getLocale(), actual.getLocale());
        assertEquals(expected.getTokenVersion(), actual.getTokenVersion());
        assertEquals(expected.getPasswordVersion(), actual.getPasswordVersion());
        assertEquals(expected.isSessionExpirationOverride(), actual.isSessionExpirationOverride());
        assertEquals(expected.getSessionExpirationPeriods(), actual.getSessionExpirationPeriods());
        assertEquals(expected.getSessionExpirationPeriodType(), actual.getSessionExpirationPeriodType());
        assertEquals(expected.getOrganization(), actual.getOrganization());
        assertEquals(expected.getOrganizationalRole(), actual.getOrganizationalRole());
        assertEquals(expected.getCreated().getTime(), actual.getCreated().getTime());
        assertEquals(expected.getEmailVerifiedDate(), actual.getEmailVerifiedDate());
        assertEquals(expected.getData(), actual.getData());
        assertRoles(expected.getRoles(), actual.getRoles());

    }

    @Override
    User newVO(User owner) {
        User user = new User();
        user.setName("usersServiceTest");
        user.setUsername(UUID.randomUUID().toString());
        user.setPassword(Common.encrypt("usersServiceTest"));
        user.setEmail(UUID.randomUUID().toString() + "@example.com");
        user.setPhone("");
        user.setRoles(Collections.unmodifiableSet(new HashSet<>(Arrays.asList(readRole, editRole))));
        user.setDisabled(false);
        return user;
    }

    @Override
    User updateVO(User existing) {
        existing.setName("usersServiceTest2");
        existing.setPassword(Common.encrypt("usersServiceTest2"));
        existing.setEmail(UUID.randomUUID().toString() + "@example.com");
        existing.setPhone("");
        existing.setRoles(Collections.unmodifiableSet(new HashSet<>(Arrays.asList(readRole, editRole, setRole))));
        existing.setDisabled(false);
        return existing;
    }


    @Test
    @Override
    public void testUserCanDelete() {
        //Nothing as you cannot delete another user unless you are superadmin
    }

    @Test
    public void testUsernameUnique() {
        User user = insertNewVO(readUser);
        assertFalse(getDao().isUsernameUnique(user.getUsername(), Common.NEW_ID));
        assertTrue(getDao().isUsernameUnique(user.getUsername(), user.getId()));
    }

    @Test
    public void testEmailUnique() {
        User user = insertNewVO(readUser);
        assertFalse(getDao().isEmailUnique(user.getEmail(), Common.NEW_ID));
        assertTrue(getDao().isEmailUnique(user.getEmail(), user.getId()));
    }

    @Test
    public void getUserByEmail() {
        User user = insertNewVO(readUser);
        User dbUser = service.getUserByEmail(user.getEmail());
        assertVoEqual(user, dbUser);
    }

    @Test
    public void getDisabledUsers() {
        User user = insertNewVO(readUser);
        user.setDisabled(true);
        service.update(user.getId(), user);
        List<User> active = getDao().getActiveUsers();
        List<User> all = getDao().getAll();
        assertEquals(all.size() - 1, active.size());
    }

    @Override
    void addReadRoleToFail(Role role, User vo) {
        vo.getRoles().add(role);
    }

    @Override
    String getReadRolesContextKey() {
        return "roles";
    }

    @Override
    void addEditRoleToFail(Role role, User vo) {
        throw new UnsupportedOperationException();
    }

    @Override
    String getEditRolesContextKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    void assertRoles(Set<Role> expected, Set<Role> actual) {
        assertEquals(expected.size(), actual.size());
        Set<Role> missing = new HashSet<>();
        for(Role expectedRole : expected) {
            boolean found = false;
            for(Role actualRole : actual) {
                if(StringUtils.equals(expectedRole.getXid(), actualRole.getXid())) {
                    found = true;
                    break;
                }
            }
            if(!found) {
                missing.add(expectedRole);
            }
        }
        if(missing.size() > 0) {
            String missingRoles = "";
            for(Role missingRole : missing) {
                missingRoles += "< " + missingRole.getId() + " - " + missingRole.getXid() + "> ";
            }
            fail("Not all roles matched, missing: " + missingRoles);
        }
    }
}
