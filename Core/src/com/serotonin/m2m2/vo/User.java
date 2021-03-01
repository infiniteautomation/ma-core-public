/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */
package com.serotonin.m2m2.vo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.infiniteautomation.mango.permission.MangoPermission;
import com.infiniteautomation.mango.spring.service.PasswordService;
import com.infiniteautomation.mango.spring.service.PermissionService;
import com.infiniteautomation.mango.spring.service.UsersService;
import com.infiniteautomation.mango.util.LazyInitializer;
import com.serotonin.ShouldNeverHappenException;
import com.serotonin.json.JsonException;
import com.serotonin.json.JsonReader;
import com.serotonin.json.ObjectWriter;
import com.serotonin.json.spi.JsonProperty;
import com.serotonin.json.spi.JsonSerializable;
import com.serotonin.json.type.JsonArray;
import com.serotonin.json.type.JsonObject;
import com.serotonin.json.type.JsonValue;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.db.dao.UserDao;
import com.serotonin.m2m2.i18n.TranslatableJsonException;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.i18n.Translations;
import com.serotonin.m2m2.rt.dataImage.SetPointSource;
import com.serotonin.m2m2.rt.event.AlarmLevels;
import com.serotonin.m2m2.vo.permission.PermissionHolder;
import com.serotonin.m2m2.vo.role.Role;
import com.serotonin.m2m2.web.mvc.spring.security.authentication.MangoUserDetailsService;

public class User extends AbstractVO implements SetPointSource, JsonSerializable, UserDetails, PermissionHolder {

    public final static String PLAIN_TEXT_ALGORITHM = "PLAINTEXT";
    public final static String NONE_ALGORITHM = "NONE";
    public final static String BCRYPT_ALGORITHM = "BCRYPT";
    public final static String LOCKED_ALGORITHM = "LOCKED";
    public final static String SHA1_ALGORITHM = "SHA-1";

    @JsonProperty
    private String username;
    /**
     * This field actually stores a password hash with the hashing algorithm prefixed.
     * e.g. {BCRYPT}hash
     */
    @JsonProperty
    private String password;
    @JsonProperty
    private String email;
    @JsonProperty
    private String phone;
    @JsonProperty
    private boolean disabled;
    @JsonProperty
    private String homeUrl;
    private long lastLogin;
    //Receive emails for alarm levels >= this
    @JsonProperty
    private AlarmLevels receiveAlarmEmails = AlarmLevels.IGNORE;
    @JsonProperty
    private boolean receiveOwnAuditEvents;
    @JsonProperty
    private String timezone;
    @JsonProperty
    private boolean muted = true;
    @JsonProperty
    private String locale;

    private int tokenVersion;
    private int passwordVersion;
    private long passwordChangeTimestamp;
    @JsonProperty
    private boolean sessionExpirationOverride;
    @JsonProperty
    private int sessionExpirationPeriods;
    @JsonProperty
    private String sessionExpirationPeriodType;
    @JsonProperty
    private String organization;
    @JsonProperty
    private String organizationalRole;
    @JsonProperty
    private Date created;
    @JsonProperty
    private Date emailVerified;
    @JsonProperty
    private JsonNode data;
    @JsonProperty
    private MangoPermission readPermission = new MangoPermission();
    @JsonProperty
    private MangoPermission editPermission = new MangoPermission();
    @JsonProperty
    private volatile Set<Role> roles = Collections.singleton(PermissionHolder.USER_ROLE);

    //
    // Session data. The user object is stored in session, and some other session-based information is cached here
    // for convenience.
    //
    private transient LazyInitializer<ZoneId> _tz;
    private transient LazyInitializer<Locale> localeObject;

    //
    //Spring Security
    //
    private transient LazyInitializer<Set<GrantedAuthority>> authorities;
    private transient LazyInitializer<PasswordService> passwordService;

    public User() {
        this.name = "";
        this.timezone = "";
        this.locale = "";

        this.tokenVersion = 1;
        this.passwordVersion = 1;

        this.initializeTransients();
    }

    @Override
    public String getXid() {
        return username;
    }
    /**
     * Used for various display purposes.
     */
    public String getDescription() {
        return username + " (" + id + ")";
    }

    public boolean isFirstLogin() {
        return lastLogin == 0;
    }

    //
    //
    // SetPointSource implementation
    //
    @Override
    public int getSetPointSourceId() {
        return id;
    }

    @Override
    public String getSetPointSourceType() {
        return "USER";
    }

    @Override
    public TranslatableMessage getSetPointSourceMessage() {
        return new TranslatableMessage("annotation.user", username);
    }

    @Override
    public void raiseRecursionFailureEvent() {
        throw new ShouldNeverHappenException("");
    }

    //
    //
    // Properties
    //

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return password;
    }

    /**
     * If the password field doesn't have an algorithm encoded in it, assume it's SHA-1 (legacy JSON) and update it
     *
     * @return true if the password field was updated, i.e. there was no algorithm in the password field
     */
    public boolean assumeSha1Algorithm() {
        if (this.password != null && !this.password.isEmpty()) {
            Matcher m = Common.EXTRACT_ALGORITHM_HASH.matcher(this.password);
            if (!m.matches()) {
                this.password = "{" + SHA1_ALGORITHM + "}" + this.password;
                return true;
            }
        }
        return false;
    }

    /**
     * Checks the password field, if the algorithm is PLAINTEXT then it will hash it using the default
     * hash algorithm (BCRYPT)
     *
     * @return true if the password field was updated, i.e. the password was plain text
     */
    public boolean hashPlainText() {
        Matcher m = Common.EXTRACT_ALGORITHM_HASH.matcher(this.password);
        if (m.matches()) {
            String algorithm = m.group(1);
            String plainText = m.group(2);

            // if the algorithm is set to PLAINTEXT we are going to hash the password
            if (PLAIN_TEXT_ALGORITHM.equals(algorithm)) {
                this.password = Common.encrypt(plainText);
                return true;
            }
        }
        return false;
    }

    /**
     * Sets a new plaintext password, i.e. prepends it with {PLAINTEXT} so it will be hashed upon saving
     * (after validation).
     *
     * @param password
     */
    public void setPlainTextPassword(String password) {
        this.setPasswordHash(PLAIN_TEXT_ALGORITHM, password);
    }

    /**
     * Sets the password field to the given algorithm and hash. It is saved in the form {HASH_ALGORITHM}passwordHash
     *
     * @param algorithm
     * @param hash
     */
    public void setPasswordHash(String algorithm, String hash) {
        this.password = "{" + algorithm + "}" + hash;
    }

    /**
     * Set the password field, this is actually a password hash, or a plaintext string that will be hashed upon
     * database insertion/update. It takes the form {HASH_ALGORITHM}passwordHash
     *
     * You shouldn't use this directly in most cases (except in tests). Use setPlainTextPassword() or setPasswordHash() instead.
     *
     * @param password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public String getHomeUrl() {
        return homeUrl;
    }

    public void setHomeUrl(String homeUrl) {
        this.homeUrl = homeUrl;
    }

    public long getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(long lastLogin) {
        this.lastLogin = lastLogin;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public AlarmLevels getReceiveAlarmEmails() {
        return receiveAlarmEmails;
    }

    public void setReceiveAlarmEmails(AlarmLevels receiveAlarmEmails) {
        this.receiveAlarmEmails = receiveAlarmEmails;
    }

    public boolean isReceiveOwnAuditEvents() {
        return receiveOwnAuditEvents;
    }

    public void setReceiveOwnAuditEvents(boolean receiveOwnAuditEvents) {
        this.receiveOwnAuditEvents = receiveOwnAuditEvents;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
        this._tz.reset();
    }

    @Override
    public ZoneId getZoneId() {
        return this._tz.get(() -> {
            ZoneId tz = null;
            if (!StringUtils.isEmpty(timezone))
                tz = ZoneId.of(timezone);
            if (tz == null)
                tz = ZoneId.systemDefault();
            return tz;
        });
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
        this.localeObject.reset();
    }

    public void setPasswordChangeTimestamp(long timestamp){
        this.passwordChangeTimestamp = timestamp;
    }

    public long getPasswordChangeTimestamp() {
        return this.passwordChangeTimestamp;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getOrganizationalRole() {
        return organizationalRole;
    }

    public void setOrganizationalRole(String organizationalRole) {
        this.organizationalRole = organizationalRole;
    }

    public Date getCreated() {
        return created;
    }

    public Long getCreatedTs() {
        return created == null ? null : created.getTime();
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getEmailVerifiedDate() {
        return emailVerified;
    }

    public Long getEmailVerifiedTimestamp() {
        return emailVerified == null ? null : emailVerified.getTime();
    }

    public void setEmailVerifiedDate(Date emailVerified) {
        this.emailVerified = emailVerified;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.authorities.get(() -> {
            //TODO Mango 4.0 see #1610
            Set<Role> allInheritedRoles = Common.getBean(PermissionService.class).getAllInheritedRoles(this);
            return Collections.unmodifiableSet(MangoUserDetailsService.getGrantedAuthorities(allInheritedRoles));
        });
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; //Don't have this feature
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; //Don't have this feature
    }

    @Override
    public boolean isCredentialsNonExpired() {
        PasswordService passwordService = this.passwordService.get(() -> Common.getBean(PasswordService.class));
        return !passwordService.passwordExpired(this);
    }

    @Override
    public boolean isEnabled() {
        return !this.disabled;
    }

    public boolean isSessionExpirationOverride() {
        return sessionExpirationOverride;
    }

    public void setSessionExpirationOverride(boolean sessionExpirationOverride) {
        this.sessionExpirationOverride = sessionExpirationOverride;
    }

    public int getSessionExpirationPeriods() {
        return sessionExpirationPeriods;
    }

    public void setSessionExpirationPeriods(int sessionExpirationPeriods) {
        this.sessionExpirationPeriods = sessionExpirationPeriods;
    }

    public String getSessionExpirationPeriodType() {
        return sessionExpirationPeriodType;
    }

    public void setSessionExpirationPeriodType(String sessionExpirationPeriodType) {
        this.sessionExpirationPeriodType = sessionExpirationPeriodType;
    }

    @Override
    public String toString() {
        return "User [id=" + id + ", username=" + username + ", password=<redacted>" + ", email=" + email + ", phone="
                + phone + ", disabled=" + disabled + ", homeUrl=" + homeUrl + ", lastLogin="
                + lastLogin + ", receiveAlarmEmails=" + receiveAlarmEmails + ", receiveOwnAuditEvents="
                + receiveOwnAuditEvents + ", timezone=" + timezone + ", name=" + name + ", locale=" + locale + ", roles=" + roles + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        return result;
    }

    /**
     * User is considered to be equal if its id is equal.
     * This implementation is relied upon by MangoSessionRegistry
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final User other = (User) obj;
        if (id != other.id)
            return false;
        return true;
    }

    @Override
    public void jsonWrite(ObjectWriter writer) throws IOException, JsonException {
        writer.writeEntry("name", name);
        UsersService usersService = Common.getBean(UsersService.class);

        // TODO Mango 4.0 remove this check after user read/write permissions are added
        // this method is called when raising an audit event
        // users with create permission do not have read permission for the user they created
        if (usersService.hasReadPermission(Common.getUser(), this)) {
            List<LinkedAccount> linkedAccounts = usersService.getLinkedAccounts(this);
            writer.writeEntry("linkedAccounts", linkedAccounts);
        }
    }

    @Override
    public void jsonRead(JsonReader reader, JsonObject jsonObject) throws JsonException {
        name = jsonObject.getString("name");
        if(name == null)
            name = username;
        this.roles = readLegacyPermissions("permissions", this.roles, jsonObject);
    }

    @Override
    public String getTypeKey() {
        return "event.audit.user";
    }

    @Override
    public Locale getLocaleObject() {
        return this.localeObject.get(() -> {
            if (locale == null || locale.isEmpty()) {
                return Common.getLocale();
            }

            try {
                return new Locale.Builder().setLanguageTag(locale).build();
            } catch (IllformedLocaleException e) {
                return Common.getLocale();
            }
        });
    }

    /**
     * Get the translations for a User's locale
     * @return
     */
    public Translations getTranslations(){
        return Translations.getTranslations(getLocaleObject());
    }

    /**
     *
     */
    private static final long serialVersionUID = -1L;

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(int tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public int getPasswordVersion() {
        return passwordVersion;
    }

    public void setPasswordVersion(int passwordVersion) {
        this.passwordVersion = passwordVersion;
    }

    public boolean isPasswordLocked() {
        return this.password != null && this.password.startsWith(UserDao.LOCKED_PASSWORD);
    }

    @Override
    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
        this.authorities.reset();
    }

    @Override
    public String getPermissionHolderName() {
        return this.username;
    }

    @Override
    public boolean isPermissionHolderDisabled() {
        return this.disabled;
    }

    public MangoPermission getReadPermission() {
        return readPermission;
    }

    public void setReadPermission(MangoPermission readPermission) {
        this.readPermission = readPermission;
    }

    public MangoPermission getEditPermission() {
        return editPermission;
    }

    public void setEditPermission(MangoPermission editPermission) {
        this.editPermission = editPermission;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.initializeTransients();
    }

    private void initializeTransients() {
        this._tz = new LazyInitializer<>();
        this.localeObject = new LazyInitializer<>();
        this.authorities = new LazyInitializer<>();
        this.passwordService = new LazyInitializer<>();
    }

    Set<Role> readLegacyPermissions(String permissionName, Set<Role> existing, JsonObject jsonObject) throws TranslatableJsonException {
        //Legacy permissions support
        PermissionService permissionService = Common.getBean(PermissionService.class);
        if(jsonObject.containsKey(permissionName)) {
            Set<Role> roles;
            if(existing != null) {
                roles = new HashSet<>(existing);
            }else {
                roles = new HashSet<>();
            }
            //Try string format
            try {
                String groups = jsonObject.getString(permissionName);
                for(String permission : PermissionService.explodeLegacyPermissionGroups(groups)) {
                    Role role = permissionService.getRole(permission);
                    if(role != null) {
                        roles.add(role);
                    } else {
                        throw new TranslatableJsonException("emport.error.missingRole", permission, permissionName);
                    }
                }

            }catch(ClassCastException e) {
                //Might be an array
                //Try array
                try {
                    JsonArray permissions = jsonObject.getJsonArray(permissionName);
                    for(JsonValue jv : permissions) {
                        Role role = permissionService.getRole(jv.toString());
                        if(role != null) {
                            roles.add(role);
                        } else {
                            throw new TranslatableJsonException("emport.error.missingRole", jv.toString(), permissionName);
                        }
                    }
                }catch(ClassCastException e2) {
                    throw e2; //Give up
                }
            }
            return Collections.unmodifiableSet(roles);
        }
        return Collections.emptySet();
    }
}
