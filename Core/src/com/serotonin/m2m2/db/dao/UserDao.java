/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */
package com.serotonin.m2m2.db.dao;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jooq.BatchBindStep;
import org.jooq.Field;
import org.jooq.InsertValuesStep3;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infiniteautomation.mango.db.query.RQLOperation;
import com.infiniteautomation.mango.db.query.RQLSubSelectCondition;
import com.infiniteautomation.mango.db.query.RQLToCondition.RQLVisitException;
import com.infiniteautomation.mango.db.tables.Events;
import com.infiniteautomation.mango.db.tables.MailingListMembers;
import com.infiniteautomation.mango.db.tables.OAuth2Users;
import com.infiniteautomation.mango.db.tables.Roles;
import com.infiniteautomation.mango.db.tables.UserComments;
import com.infiniteautomation.mango.db.tables.UserRoleMappings;
import com.infiniteautomation.mango.db.tables.Users;
import com.infiniteautomation.mango.db.tables.records.OAuth2UsersRecord;
import com.infiniteautomation.mango.db.tables.records.UsersRecord;
import com.infiniteautomation.mango.spring.MangoRuntimeContextConfiguration;
import com.infiniteautomation.mango.spring.events.DaoEvent;
import com.infiniteautomation.mango.spring.events.DaoEventType;
import com.infiniteautomation.mango.spring.events.audit.ChangeAuditEvent;
import com.infiniteautomation.mango.spring.service.PermissionService;
import com.infiniteautomation.mango.util.LazyInitSupplier;
import com.infiniteautomation.mango.util.exception.NotFoundException;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.event.AlarmLevels;
import com.serotonin.m2m2.rt.event.type.AuditEventType;
import com.serotonin.m2m2.vo.LinkedAccount;
import com.serotonin.m2m2.vo.OAuth2LinkedAccount;
import com.serotonin.m2m2.vo.User;
import com.serotonin.m2m2.vo.permission.PermissionHolder;
import com.serotonin.m2m2.vo.role.Role;
import com.serotonin.m2m2.web.mvc.spring.security.MangoSessionRegistry;

/**
 * @author Terry Packer
 */
@Repository
public class UserDao extends AbstractVoDao<User, UsersRecord, Users> {
    private static final Log LOG = LogFactory.getLog(UserDao.class);

    private static final LazyInitSupplier<UserDao> springInstance = new LazyInitSupplier<>(() -> Common.getRuntimeContext().getBean(UserDao.class));

    private final UserRoleMappings userRoleMappings;
    private final Roles roles;
    private final RoleDao roleDao;
    private final OAuth2Users oauth;
    private final UserComments userComments;
    private final Events events;
    private final MailingListMembers mailingListMembers;

    @Autowired
    private UserDao(PermissionService permissionService,
                    @Qualifier(MangoRuntimeContextConfiguration.DAO_OBJECT_MAPPER_NAME) ObjectMapper mapper,
                    ApplicationEventPublisher publisher, RoleDao roleDao) {
        super(AuditEventType.TYPE_USER,
                Users.USERS,
                new TranslatableMessage("internal.monitor.USER_COUNT"),
                mapper, publisher, permissionService);
        this.roleDao = roleDao;
        this.oauth = OAuth2Users.O_AUTH2_USERS;
        this.roles = Roles.ROLES;
        this.userRoleMappings = UserRoleMappings.USER_ROLE_MAPPINGS;
        this.userComments = UserComments.USER_COMMENTS;
        this.events = Events.EVENTS;
        this.mailingListMembers = MailingListMembers.MAILING_LIST_MEMBERS;
    }

    /**
     * Get cached instance from Spring Context
     *
     * @return instance
     */
    public static UserDao getInstance() {
        return springInstance.get();
    }

    /**
     * Required so getXidById() method works.
     */
    public Field<String> getXidField() {
        return table.username;
    }

    @Override
    protected Map<String, Field<?>> createFieldMap() {
        Map<String, Field<?>> fields = super.createFieldMap();
        fields.put("lastPasswordChange", table.passwordChangeTimestamp);
        fields.put("created", table.createdTs);
        fields.put("emailVerified", table.emailVerifiedTs);
        return fields;
    }

    @Override
    protected Map<String, Function<Object, Object>> createValueConverterMap() {
        Map<String, Function<Object, Object>> converters = super.createValueConverterMap();
        converters.put("receiveAlarmEmails", v -> {
            if (v instanceof String) {
                return AlarmLevels.fromName((String) v).value();
            }
            return v;
        });
        return converters;
    }

    @Override
    protected Map<String, RQLSubSelectCondition> createSubSelectMap() {
        Map<String, RQLSubSelectCondition> subSelectMap = super.createSubSelectMap();
        subSelectMap.put("roles", createUserRoleCondition());
        subSelectMap.put("inheritedRoles", createUserInheritedRoleCondition());
        return subSelectMap;
    }

    /**
     * Confirm that this username is not used
     *
     * @param username  username to check
     * @param excludeId user id to exclude from check
     * @return true if the username is not in use
     */
    public boolean isUsernameUnique(String username, int excludeId) {
        if (username == null) {
            return false;
        } else {
            return this.getCountQuery().from(table).where(
                    table.username.equal(username),
                    table.id.notEqual(excludeId)).fetchSingleInto(Integer.class) == 0;
        }
    }

    /**
     * Confirm that this email address is not used
     *
     * @param email     email address to check
     * @param excludeId user id to exclude from check
     * @return true if the email address is not in use
     */
    public boolean isEmailUnique(String email, int excludeId) {
        if (email == null) {
            return false;
        } else {
            return this.getCountQuery().from(table).where(
                    table.email.equal(email),
                    table.id.notEqual(excludeId)).fetchSingleInto(Integer.class) == 0;
        }
    }

    @Override
    public User getByXid(String username) {
        if (username == null) return null;
        return getJoinedSelectQuery()
                .where(table.username.equalIgnoreCase(username))
                .fetchOne(this::mapRecordLoadRelationalData);
    }

    /**
     * Get the roles for a user from the database mapping table
     *
     * @param vo the user
     * @return a set of roles that the user has been directly assigned (not inherited roles)
     */
    public Set<Role> getUserRoles(User vo) {
        try (Stream<? extends Record> stream = create.select(roles.id, roles.xid)
                .from(userRoleMappings)
                .join(roles).on(userRoleMappings.roleId.equal(roles.id))
                .where(userRoleMappings.userId.equal(vo.getId()))
                .stream()) {
            return Collections.unmodifiableSet(stream.map(roleDao::mapRecordToRole).collect(Collectors.toSet()));
        }
    }

    @Override
    public void loadRelationalData(User vo) {
        vo.setRoles(getUserRoles(vo));
    }

    @Override
    public void saveRelationalData(User existing, User vo) {
        if (existing != null) {
            //delete role mappings
            create.deleteFrom(userRoleMappings)
                    .where(userRoleMappings.userId.equal(vo.getId()))
                    .execute();
        }

        //insert role mappings
        BatchBindStep b = create.batch(
                DSL.insertInto(userRoleMappings)
                        .columns(userRoleMappings.roleId, userRoleMappings.userId)
                        .values((Integer) null, null));

        for (Role role : vo.getRoles()) {
            b.bind(role.getId(), vo.getId());
        }
        b.execute();
    }

    /**
     * Get a user by their email address
     *
     * @param emailAddress the email address of the user to find
     * @return the user corresponding to the email address (or null if not found)
     */
    public User getUserByEmail(String emailAddress) {
        if (emailAddress == null) return null;
        return getJoinedSelectQuery()
                .where(table.email.equal(emailAddress))
                .fetchOne(this::mapRecordLoadRelationalData);
    }

    public List<User> getActiveUsers() {
        return getJoinedSelectQuery()
                .where(table.disabled.equal(boolToChar(false)))
                .fetch(this::mapRecordLoadRelationalData);
    }

    @Override
    public void insert(User vo) {
        // ensure passwords prefixed with {PLAINTEXT} are always hashed before database insertion/update
        // we hash plain text passwords after validation has taken place so we can check complexity etc
        vo.hashPlainText();
        if (vo.getCreatedTs() == null) {
            vo.setCreated(new Date(Common.timer.currentTimeMillis()));
        }
        enforceUserRole(vo);
        super.insert(vo);
    }

    @Override
    public void update(User existing, User vo) {
        // ensure passwords prefixed with {PLAINTEXT} are always hashed before database insertion/update
        // we hash plain text passwords after validation has taken place so we can check complexity etc
        vo.hashPlainText();
        try {
            User old = getTransactionTemplate().execute(status -> {
                User old1 = get(vo.getId());
                if (old1 == null) {
                    return null;
                }
                enforceUserRole(vo);
                boolean passwordChanged = !old1.getPassword().equals(vo.getPassword());
                if (passwordChanged) {
                    vo.setPasswordChangeTimestamp(Common.timer.currentTimeMillis());
                    vo.setPasswordVersion(old1.getPasswordVersion() + 1);
                } else {
                    vo.setPasswordChangeTimestamp(old1.getPasswordChangeTimestamp());
                    vo.setPasswordVersion(old1.getPasswordVersion());
                }
                UserDao.super.update(old1, vo);
                //Set the last login time so it is available on the saved user
                vo.setLastLogin(old1.getLastLogin());

                if (passwordChanged || vo.isDisabled()) {
                    expireSessionsForUser(old1);
                }

                return old1;
            });

            if (old == null) {
                throw new NotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            // Log some information about the user object.
            LOG.error("Error updating user: " + vo, e);
            throw e;
        }
    }

    private void enforceUserRole(User vo) {
        Role userRole = PermissionHolder.USER_ROLE;
        if (!vo.getRoles().contains(userRole)) {
            Set<Role> updated = new HashSet<>(vo.getRoles());
            updated.add(userRole);
            vo.setRoles(Collections.unmodifiableSet(updated));
        }
    }

    // TODO Mango 4.0 add event listener to MangoSessionRegistry
    private void expireSessionsForUser(User user) {
        // web context may not be initialized, can't inject this context
        ApplicationContext context = Common.getRootWebContext();
        if (context != null) {
            MangoSessionRegistry sessionRegistry = context.getBean(MangoSessionRegistry.class);
            sessionRegistry.exireSessionsForUser(user);
        }
    }

    @Override
    public boolean delete(User vo) {
        boolean deleted = super.delete(vo);
        if (deleted) {
            // expire the user's sessions
            expireSessionsForUser(vo);
        }
        return deleted;
    }

    @Override
    public void deleteRelationalData(User vo) {
        create.update(userComments).setNull(userComments.userId).where(userComments.userId.equal(vo.getId())).execute();
        create.deleteFrom(mailingListMembers).where(mailingListMembers.userId.equal(vo.getId())).execute();
        create.update(events).setNull(events.ackUserId)
                .set(events.alternateAckSource, new TranslatableMessage("events.ackedByDeletedUser").serialize())
                .where(events.ackUserId.equal(vo.getId()))
                .execute();
    }

    /**
     * Revoke all tokens for user
     *
     * @param user the user who's tokens will be revoked
     */
    public void revokeTokens(User user) {
        int userId = user.getId();
        User old = get(userId);

        int currentTokenVersion = user.getTokenVersion();
        int newTokenVersion = currentTokenVersion + 1;

        int count = create.update(table)
                .set(table.tokenVersion, newTokenVersion)
                .where(
                        table.id.equal(user.getId()),
                        table.tokenVersion.equal(currentTokenVersion),
                        table.username.equal(user.getUsername())
                ).execute();

        if (count == 0) {
            throw new EmptyResultDataAccessException("Updated no rows", 1);
        }

        user.setTokenVersion(newTokenVersion);
        eventPublisher.publishEvent(new DaoEvent<>(this, DaoEventType.UPDATE, user, old));
    }

    public static final String LOCKED_PASSWORD = "{" + User.LOCKED_ALGORITHM + "}";

    public void lockPassword(User user) {
        updatePasswordHash(user, LOCKED_PASSWORD);
    }

    public void updatePasswordHash(User user, String newPasswordHash) {
        int userId = user.getId();
        User old = get(userId);

        int currentPasswordVersion = user.getPasswordVersion();
        int newPasswordVersion = currentPasswordVersion + 1;
        long passwordChangeTimestamp = Common.timer.currentTimeMillis();

        int count = create.update(table)
                .set(table.password, newPasswordHash)
                .set(table.passwordVersion, newPasswordVersion)
                .set(table.passwordChangeTimestamp, passwordChangeTimestamp)
                .where(
                        table.id.equal(userId),
                        table.passwordVersion.equal(currentPasswordVersion),
                        table.username.equal(user.getUsername())
                ).execute();

        if (count == 0) {
            throw new EmptyResultDataAccessException("Updated no rows", 1);
        }

        user.setPassword(newPasswordHash);
        user.setPasswordVersion(newPasswordVersion);
        user.setPasswordChangeTimestamp(passwordChangeTimestamp);

        // expire the user's sessions
        expireSessionsForUser(user);
        eventPublisher.publishEvent(new DaoEvent<>(this, DaoEventType.UPDATE, user, old));
    }

    public void recordLogin(User user) {
        User old = get(user.getId());

        Objects.requireNonNull(old);
        Objects.requireNonNull(user);

        long loginTime = Common.timer.currentTimeMillis();
        user.setLastLogin(loginTime);
        create.update(table).set(table.lastLogin, loginTime)
                .where(table.id.equal(user.getId())).execute();
        eventPublisher.publishEvent(new DaoEvent<>(this, DaoEventType.UPDATE, user, old));
    }

    public void saveHomeUrl(int userId, String homeUrl) {
        User old = get(userId);
        create.update(table).set(table.homeUrl, homeUrl)
                .where(table.id.equal(userId)).execute();
        User user = get(userId);
        publishAuditEvent(new ChangeAuditEvent<>(this.auditEventType, Common.getUser(), old, user));
        eventPublisher.publishEvent(new DaoEvent<>(this, DaoEventType.UPDATE, user, old));
    }

    public void saveMuted(int userId, boolean muted) {
        User old = get(userId);
        create.update(table).set(table.muted, boolToChar(muted))
                .where(table.id.equal(userId)).execute();
        User user = get(userId);
        publishAuditEvent(new ChangeAuditEvent<>(this.auditEventType, Common.getUser(), old, user));
        eventPublisher.publishEvent(new DaoEvent<>(this, DaoEventType.UPDATE, user, old));
    }

    @Override
    protected Record toRecord(User vo) {
        Record record = table.newRecord();
        record.set(table.username, vo.getUsername());
        record.set(table.name, vo.getName());
        record.set(table.password, vo.getPassword());
        record.set(table.email, vo.getEmail());
        record.set(table.phone, vo.getPhone());
        record.set(table.disabled, boolToChar(vo.isDisabled()));
        record.set(table.lastLogin, vo.getLastLogin());
        record.set(table.homeUrl, vo.getHomeUrl());
        record.set(table.receiveAlarmEmails, vo.getReceiveAlarmEmails().value());
        record.set(table.receiveOwnAuditEvents, boolToChar(vo.isReceiveOwnAuditEvents()));
        record.set(table.timezone, vo.getTimezone());
        record.set(table.muted, boolToChar(vo.isMuted()));
        record.set(table.locale, vo.getLocale());
        record.set(table.tokenVersion, vo.getTokenVersion());
        record.set(table.passwordVersion, vo.getPasswordVersion());
        record.set(table.passwordChangeTimestamp, vo.getPasswordChangeTimestamp());
        record.set(table.sessionExpirationOverride, boolToChar(vo.isSessionExpirationOverride()));
        record.set(table.sessionExpirationPeriods, vo.getSessionExpirationPeriods());
        record.set(table.sessionExpirationPeriodType, vo.getSessionExpirationPeriodType());
        record.set(table.organization, vo.getOrganization());
        record.set(table.organizationalRole, vo.getOrganizationalRole());
        record.set(table.createdTs, vo.getCreatedTs());
        record.set(table.emailVerifiedTs, vo.getEmailVerifiedTimestamp());
        record.set(table.data, convertData(vo.getData()));
        return record;
    }

    @Override
    public @NonNull User mapRecord(@NonNull Record record) {
        User user = new User();
        user.setId(record.get(table.id));
        user.setUsername(record.get(table.username));
        user.setName(record.get(table.name));
        user.setPassword(record.get(table.password));
        user.setEmail(record.get(table.email));
        user.setPhone(record.get(table.phone));
        user.setDisabled(charToBool(record.get(table.disabled)));
        user.setLastLogin(record.get(table.lastLogin));
        user.setHomeUrl(record.get(table.homeUrl));
        user.setReceiveAlarmEmails(AlarmLevels.fromValue(record.get(table.receiveAlarmEmails)));
        user.setReceiveOwnAuditEvents(charToBool(record.get(table.receiveOwnAuditEvents)));
        user.setTimezone(record.get(table.timezone));
        user.setMuted(charToBool(record.get(table.muted)));
        user.setLocale(record.get(table.locale));
        user.setTokenVersion(record.get(table.tokenVersion));
        user.setPasswordVersion(record.get(table.passwordVersion));
        user.setPasswordChangeTimestamp(record.get(table.passwordChangeTimestamp));
        user.setSessionExpirationOverride(charToBool(record.get(table.sessionExpirationOverride)));
        Integer sessionExpirationPeriods = record.get(table.sessionExpirationPeriods);
        if (sessionExpirationPeriods != null) {
            user.setSessionExpirationPeriods(sessionExpirationPeriods);
        }
        user.setSessionExpirationPeriodType(record.get(table.sessionExpirationPeriodType));
        user.setOrganization(record.get(table.organization));
        user.setOrganizationalRole(record.get(table.organizationalRole));
        user.setCreated(new Date(record.get(table.createdTs)));
        Long emailVerifiedTs = record.get(table.emailVerifiedTs);
        if (emailVerifiedTs != null) {
            user.setEmailVerifiedDate(new Date(emailVerifiedTs));
        }
        user.setData(extractDataFromObject(record.get(table.data)));
        return user;
    }

    @Override
    protected String getXidPrefix() {
        return "";
    }

    public Optional<User> getUserForLinkedAccount(LinkedAccount account) {
        if (account instanceof OAuth2LinkedAccount) {
            OAuth2LinkedAccount oAuth2Account = (OAuth2LinkedAccount) account;
            return create.select(table.fields())
                    .from(oauth)
                    .leftJoin(table).on(oauth.userId.equal(table.id))
                    .where(oauth.issuer.equal(oAuth2Account.getIssuer()).and(oauth.subject.equal(oAuth2Account.getSubject())))
                    .fetchOptional(this::mapRecord);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public void linkAccount(int userId, LinkedAccount account) {
        if (account instanceof OAuth2LinkedAccount) {
            OAuth2LinkedAccount oAuth2Account = (OAuth2LinkedAccount) account;
            create.insertInto(oauth)
                    .set(oauth.userId, userId)
                    .set(oauth.issuer, oAuth2Account.getIssuer())
                    .set(oauth.subject, oAuth2Account.getSubject())
                    .execute();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public void updateLinkedAccounts(int userId, Iterable<? extends LinkedAccount> accounts) {
        this.doInTransaction(txStatus -> {
            create.deleteFrom(oauth).where(oauth.userId.equal(userId)).execute();
            InsertValuesStep3<OAuth2UsersRecord, Integer, String, String> insert = create.insertInto(oauth, oauth.userId, oauth.issuer, oauth.subject);
            for (LinkedAccount account : accounts) {
                if (account instanceof OAuth2LinkedAccount) {
                    OAuth2LinkedAccount oAuth2Account = (OAuth2LinkedAccount) account;
                    insert = insert.values(userId, oAuth2Account.getIssuer(), oAuth2Account.getSubject());
                } else {
                    throw new UnsupportedOperationException();
                }
            }
            insert.execute();
        });
    }

    public List<LinkedAccount> getLinkedAccounts(int userId) {
        return create.select(oauth.issuer, oauth.subject)
                .from(oauth)
                .where(oauth.userId.equal(userId))
                .fetch(r -> new OAuth2LinkedAccount(r.get(oauth.issuer), r.get(oauth.subject)));
    }

    /**
     * Create an RQL mapping to allow querying on user.roles
     *
     * @return a sub-select condition
     */
    public RQLSubSelectCondition createUserRoleCondition() {
        return (operation, node) -> {
            List<Object> arguments = node.getArguments();

            //Check the role Xid input
            if (arguments.size() > 2) {
                throw new RQLVisitException(String.format("Only single arguments supported for node type '%s'", node.getName()));
            }

            Object roleXid = arguments.get(1);
            Integer roleId = null;

            Role role = permissionService.getRole((String) roleXid);
            if (role != null) {
                roleId = role.getId();
            }

            SelectConditionStep<Record1<Integer>> afterWhere = create.select(userRoleMappings.userId)
                    .from(userRoleMappings)
                    .where(userRoleMappings.roleId.equal(roleId));

            if (operation == RQLOperation.CONTAINS) {
                return table.id.in(afterWhere.asField());
            }
            throw new RQLVisitException(String.format("Unsupported node type '%s' for field '%s'", node.getName(), arguments.get(0)));
        };
    }

    /**
     * Create an RQL mapping to allow querying on user.inheritedRoles
     *
     * @return a sub-select condition
     */
    public RQLSubSelectCondition createUserInheritedRoleCondition() {
        return (operation, node) -> {
            List<Object> arguments = node.getArguments();

            //Check the role Xid input
            if (arguments.size() > 2) {
                throw new RQLVisitException(String.format("Only single arguments supported for node type '%s'", node.getName()));
            }

            Object roleXid = arguments.get(1);
            Set<Integer> roleIds = new HashSet<>();

            Set<Role> inherited = permissionService.getRolesThatInherit((String) roleXid);
            for (Role r : inherited) {
                roleIds.add(r.getId());
            }

            SelectConditionStep<Record1<Integer>> afterWhere = UserDao.this.create.select(userRoleMappings.userId)
                    .from(userRoleMappings)
                    .where(userRoleMappings.roleId.in(roleIds));

            if (operation == RQLOperation.CONTAINS) {
                return table.id.in(afterWhere.asField());
            }
            throw new RQLVisitException(String.format("Unsupported node type '%s' for field '%s'", node.getName(), arguments.get(0)));
        };
    }
}
