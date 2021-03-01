--
--    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
--    @author Matthew Lohbihler
--
--

--
-- System settings
CREATE TABLE systemSettings
(
    settingName  VARCHAR(64) NOT NULL,
    settingValue LONGTEXT,
    PRIMARY KEY (settingName)
);

--
--
-- Roles
--
CREATE TABLE roles
(
    id   INT          NOT NULL AUTO_INCREMENT,
    xid  VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);
ALTER TABLE roles
    ADD CONSTRAINT rolesUn1 UNIQUE (xid);

--
-- Role Inheritance Mappings
--
CREATE TABLE roleInheritance
(
    roleId          INT NOT NULL,
    inheritedRoleId INT NOT NULL
);
ALTER TABLE roleInheritance
    ADD CONSTRAINT roleInheritanceUn1 UNIQUE (roleId, inheritedRoleId);
ALTER TABLE roleInheritance
    ADD CONSTRAINT roleInheritanceFk1 FOREIGN KEY (roleId) REFERENCES roles (id) ON DELETE CASCADE;
ALTER TABLE roleInheritance
    ADD CONSTRAINT roleInheritanceFk2 FOREIGN KEY (inheritedRoleId) REFERENCES roles (id) ON DELETE CASCADE;

--
-- Permissions
CREATE TABLE minterms
(
    id INT(11) NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (id)
);

CREATE TABLE mintermsRoles
(
    mintermId INT(11) NOT NULL,
    roleId    INT(11) NOT NULL
);
ALTER TABLE mintermsRoles
    ADD CONSTRAINT mintermsRolesIdx1 UNIQUE (mintermId, roleId);
ALTER TABLE mintermsRoles
    ADD CONSTRAINT mintermsRolesFk1 FOREIGN KEY (mintermId) REFERENCES minterms (id) ON DELETE CASCADE ON UPDATE NO ACTION;
ALTER TABLE mintermsRoles
    ADD CONSTRAINT mintermsRolesFk2 FOREIGN KEY (roleId) REFERENCES roles (id) ON DELETE CASCADE ON UPDATE NO ACTION;
CREATE INDEX mintermsRolesFk1Idx ON mintermsRoles (mintermId ASC);
CREATE INDEX mintermsRolesFk2Idx ON mintermsRoles (roleId ASC);

CREATE TABLE permissions
(
    id INT(11) NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (id)
);

CREATE TABLE permissionsMinterms
(
    permissionId INT(11) NOT NULL,
    mintermId    INT(11) NOT NULL
);
ALTER TABLE permissionsMinterms
    ADD CONSTRAINT permissionsMintermsIdx1 UNIQUE (permissionId, mintermId);
ALTER TABLE permissionsMinterms
    ADD CONSTRAINT permissionsMintermsFk1 FOREIGN KEY (permissionId) REFERENCES permissions (id) ON DELETE CASCADE ON UPDATE NO ACTION;
ALTER TABLE permissionsMinterms
    ADD CONSTRAINT permissionsMintermsFk2 FOREIGN KEY (mintermId) REFERENCES minterms (id) ON DELETE CASCADE ON UPDATE NO ACTION;
CREATE INDEX permissionsMintermsFk1Idx ON permissionsMinterms (permissionId ASC);
CREATE INDEX permissionsMintermsFk2Idx ON permissionsMinterms (mintermId ASC);

--
-- System wide permissions
--
CREATE TABLE systemPermissions
(
    permissionType VARCHAR(255),
    permissionId   INT NOT NULL
);
ALTER TABLE systemPermissions
    ADD CONSTRAINT systemPermissionsFk1 FOREIGN KEY (permissionId) REFERENCES permissions (id) ON DELETE RESTRICT;
ALTER TABLE systemPermissions
    ADD CONSTRAINT permissionTypeUn1 UNIQUE (permissionType);

--
-- Users
CREATE TABLE users
(
    id                          INT          NOT NULL AUTO_INCREMENT,
    username                    VARCHAR(40)  NOT NULL,
    password                    VARCHAR(255) NOT NULL,
    email                       VARCHAR(255),
    phone                       VARCHAR(40),
    disabled                    CHAR(1)      NOT NULL,
    lastLogin                   BIGINT,
    homeUrl                     VARCHAR(255),
    receiveAlarmEmails          INT          NOT NULL,
    receiveOwnAuditEvents       CHAR(1)      NOT NULL,
    timezone                    VARCHAR(50),
    muted                       CHAR(1),
    name                        VARCHAR(255),
    locale                      VARCHAR(50),
    tokenVersion                INT          NOT NULL,
    passwordVersion             INT          NOT NULL,
    passwordChangeTimestamp     BIGINT       NOT NULL,
    sessionExpirationOverride   CHAR(1),
    sessionExpirationPeriods    INT,
    sessionExpirationPeriodType VARCHAR(25),
    organization                VARCHAR(80),
    organizationalRole          VARCHAR(80),
    createdTs                   BIGINT       NOT NULL,
    emailVerifiedTs             BIGINT,
    data                        LONGTEXT,
    readPermissionId            INT          NOT NULL,
    editPermissionId            INT          NOT NULL,
    PRIMARY KEY (id)
);
ALTER TABLE users
    ADD CONSTRAINT username_unique UNIQUE (username);
ALTER TABLE users
    ADD CONSTRAINT email_unique UNIQUE (email);
ALTER TABLE users
    ADD CONSTRAINT usersFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;
ALTER TABLE users
    ADD CONSTRAINT usersFk2 FOREIGN KEY (editPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;

-- Links OAuth2 users to a Mango user
CREATE TABLE oAuth2Users
(
    id      INT          NOT NULL AUTO_INCREMENT,
    issuer  VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    userId  INT          NOT NULL,
    PRIMARY KEY (id)
);
ALTER TABLE oAuth2Users ADD CONSTRAINT oAuth2UsersFk1 FOREIGN KEY (userId) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE oAuth2Users ADD CONSTRAINT oAuth2UsersUn1 UNIQUE (issuer, subject);

--
--
-- User Role Mappings
--
CREATE TABLE userRoleMappings
(
    roleId INT NOT NULL,
    userId INT NOT NULL
);
ALTER TABLE userRoleMappings
    ADD CONSTRAINT userRoleMappingsFk1 FOREIGN KEY (roleId) REFERENCES roles (id) ON DELETE CASCADE;
ALTER TABLE userRoleMappings
    ADD CONSTRAINT userRoleMappingsFk2 FOREIGN KEY (userId) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE userRoleMappings
    ADD CONSTRAINT userRoleMappingsUn1 UNIQUE (roleId, userId);


CREATE TABLE userComments
(
    id          INT           NOT NULL AUTO_INCREMENT,
    xid         VARCHAR(100)  NOT NULL,
    userId      INT,
    commentType INT           NOT NULL,
    typeKey     INT           NOT NULL,
    ts          BIGINT        NOT NULL,
    commentText VARCHAR(1024) NOT NULL,
    PRIMARY KEY (id)
);
ALTER TABLE userComments
    ADD CONSTRAINT userCommentsFk1 FOREIGN KEY (userId) REFERENCES users (id);
ALTER TABLE userComments
    ADD CONSTRAINT userCommentsUn1 UNIQUE (xid);
CREATE INDEX userComments_performance1 ON userComments (commentType ASC, typeKey ASC);

--
-- Mailing lists
CREATE TABLE mailingLists
(
    id                 INT          NOT NULL AUTO_INCREMENT,
    xid                VARCHAR(100) NOT NULL,
    name               VARCHAR(255) NOT NULL,
    receiveAlarmEmails INT          NOT NULL,
    readPermissionId   INT          NOT NULL,
    editPermissionId   INT          NOT NULL,
    PRIMARY KEY (id)
);
ALTER TABLE mailingLists
    ADD CONSTRAINT mailingListsUn1 UNIQUE (xid);
ALTER TABLE mailingLists
    ADD CONSTRAINT mailingListsFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;
ALTER TABLE mailingLists
    ADD CONSTRAINT mailingListsFk2 FOREIGN KEY (editPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;

CREATE TABLE mailingListInactive
(
    mailingListId    INT NOT NULL,
    inactiveInterval INT NOT NULL
);
ALTER TABLE mailingListInactive
    ADD CONSTRAINT mailingListInactiveFk1 FOREIGN KEY (mailingListId)
        REFERENCES mailingLists (id) ON DELETE CASCADE;

CREATE TABLE mailingListMembers
(
    mailingListId INT NOT NULL,
    typeId        INT NOT NULL,
    userId        INT,
    address       VARCHAR(255)
);
ALTER TABLE mailingListMembers
    ADD CONSTRAINT mailingListMembersFk1 FOREIGN KEY (mailingListId)
        REFERENCES mailingLists (id) ON DELETE CASCADE;


--
-- Data Sources
CREATE TABLE dataSources
(
    id               INT          NOT NULL AUTO_INCREMENT,
    xid              VARCHAR(100) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    dataSourceType   VARCHAR(40)  NOT NULL,
    data             LONGBLOB     NOT NULL,
    jsonData         LONGTEXT,
    rtdata           LONGBLOB,
    readPermissionId INT          NOT NULL,
    editPermissionId INT          NOT NULL,
    PRIMARY KEY (id)
);
ALTER TABLE dataSources
    ADD CONSTRAINT dataSourcesUn1 UNIQUE (xid);
CREATE INDEX nameIndex ON dataSources (name ASC);
ALTER TABLE dataSources
    ADD CONSTRAINT dataSourcesFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;
ALTER TABLE dataSources
    ADD CONSTRAINT dataSourcesFk2 FOREIGN KEY (editPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;

-- Time series table
CREATE TABLE timeSeries
(
    id INT NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (id)
);

--
-- Data Points
CREATE TABLE dataPoints
(
    id                        INT          NOT NULL AUTO_INCREMENT,
    xid                       VARCHAR(100) NOT NULL,
    dataSourceId              INT          NOT NULL,
    name                      VARCHAR(255),
    deviceName                VARCHAR(255),
    enabled                   CHAR(1),
    loggingType               INT,
    intervalLoggingPeriodType INT,
    intervalLoggingPeriod     INT,
    intervalLoggingType       INT,
    tolerance                 DOUBLE,
    purgeOverride             CHAR(1),
    purgeType                 INT,
    purgePeriod               INT,
    defaultCacheSize          INT,
    discardExtremeValues      CHAR(1),
    engineeringUnits          INT,
    data                      LONGBLOB     NOT NULL,
    rollup                    INT,
    dataTypeId                INT          NOT NULL,
    settable                  CHAR(1),
    jsonData                  LONGTEXT,
    seriesId                  INT          NOT NULL,
    readPermissionId          INT          NOT NULL,
    editPermissionId          INT          NOT NULL,
    setPermissionId           INT          NOT NULL,
    PRIMARY KEY (id)
);
ALTER TABLE dataPoints
    ADD CONSTRAINT dataPointsUn1 UNIQUE (xid);
ALTER TABLE dataPoints
    ADD CONSTRAINT dataPointsFk1 FOREIGN KEY (dataSourceId) REFERENCES dataSources (id);
ALTER TABLE dataPoints
    ADD CONSTRAINT dataPointsFk2 FOREIGN KEY (readPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;
ALTER TABLE dataPoints
    ADD CONSTRAINT dataPointsFk3 FOREIGN KEY (editPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;
ALTER TABLE dataPoints
    ADD CONSTRAINT dataPointsFk4 FOREIGN KEY (setPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;
ALTER TABLE dataPoints
    ADD CONSTRAINT dataPointsFk5 FOREIGN KEY (seriesId) REFERENCES timeSeries (id);

CREATE INDEX pointNameIndex ON dataPoints (name ASC);
CREATE INDEX deviceNameNameIdIndex ON dataPoints (deviceName ASC, name ASC, id ASC);
CREATE INDEX enabledIndex ON dataPoints (enabled ASC);
CREATE INDEX xidNameIndex ON dataPoints (xid ASC, name ASC);
CREATE INDEX dataSourceIdFkIndex ON dataPoints (dataSourceId ASC);

-- Data point tags
CREATE TABLE dataPointTags
(
    dataPointId INT          NOT NULL,
    tagKey      VARCHAR(255) NOT NULL,
    tagValue    VARCHAR(255) NOT NULL
);
ALTER TABLE dataPointTags
    ADD CONSTRAINT dataPointTagsUn1 UNIQUE (dataPointId, tagKey);
ALTER TABLE dataPointTags
    ADD CONSTRAINT dataPointTagsFk1 FOREIGN KEY (dataPointId) REFERENCES dataPoints (id) ON DELETE CASCADE;
CREATE INDEX dataPointTagsIndex1 ON dataPointTags (tagKey ASC, tagValue ASC);

--
--
-- Point Values (historical data)
--  the dataPointId column refers to the series id of the data point,
--  which is not necessarily the data point id
--
CREATE TABLE pointValues
(
    id          BIGINT NOT NULL AUTO_INCREMENT,
    dataPointId INT    NOT NULL,
    dataType    INT    NOT NULL,
    pointValue  DOUBLE,
    ts          BIGINT NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX pointValuesIdx1 ON pointValues (dataPointId, ts);

CREATE TABLE pointValueAnnotations
(
    pointValueId        BIGINT NOT NULL,
    textPointValueShort VARCHAR(128),
    textPointValueLong  LONGTEXT,
    sourceMessage       LONGTEXT,
    PRIMARY KEY (pointValueId)
);

--
--
-- Event detectors
--
CREATE TABLE eventDetectors
(
    id               INT          NOT NULL AUTO_INCREMENT,
    xid              VARCHAR(100) NOT NULL,
    sourceTypeName   VARCHAR(32)  NOT NULL,
    typeName         VARCHAR(32)  NOT NULL,
    dataPointId      INT,
    data             LONGTEXT     NOT NULL,
    jsonData         LONGTEXT,
    readPermissionId INT          NOT NULL,
    editPermissionId INT          NOT NULL,
    PRIMARY KEY (id)
);
ALTER TABLE eventDetectors
    ADD CONSTRAINT eventDetectorsUn1 UNIQUE (xid);
ALTER TABLE eventDetectors
    ADD CONSTRAINT dataPointIdFk FOREIGN KEY (dataPointId) REFERENCES dataPoints (id);
ALTER TABLE eventDetectors
    ADD CONSTRAINT eventDetectorsFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;
ALTER TABLE eventDetectors
    ADD CONSTRAINT eventDetectorsFk2 FOREIGN KEY (editPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;

--
--
-- Events
--
CREATE TABLE events
(
    id                 INT         NOT NULL AUTO_INCREMENT,
    typeName           VARCHAR(32) NOT NULL,
    subTypeName        VARCHAR(32),
    typeRef1           INT         NOT NULL,
    typeRef2           INT         NOT NULL,
    activeTs           BIGINT      NOT NULL,
    rtnApplicable      CHAR(1)     NOT NULL,
    rtnTs              BIGINT,
    rtnCause           INT,
    alarmLevel         INT         NOT NULL,
    message            LONGTEXT,
    ackTs              BIGINT,
    ackUserId          INT,
    alternateAckSource LONGTEXT,
    readPermissionId   INT         NOT NULL,
    PRIMARY KEY (id)
);
ALTER TABLE events
    ADD CONSTRAINT eventsFk1 FOREIGN KEY (ackUserId) REFERENCES users (id);
ALTER TABLE events
    ADD CONSTRAINT eventsFk2 FOREIGN KEY (readPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;
CREATE INDEX events_performance1 ON events (activeTs ASC);
CREATE INDEX events_performance2 ON events (rtnApplicable ASC, rtnTs ASC);
CREATE INDEX events_performance3 ON events (typeName ASC, subTypeName ASC, typeRef1 ASC);

--
--
-- Event handlers
--
CREATE TABLE eventHandlers
(
    id               INT          NOT NULL AUTO_INCREMENT,
    xid              VARCHAR(100) NOT NULL,
    alias            VARCHAR(255) NOT NULL,
    eventHandlerType VARCHAR(40)  NOT NULL,
    readPermissionId INT          NOT NULL,
    editPermissionId INT          NOT NULL,
    data             LONGBLOB     NOT NULL,
    PRIMARY KEY (id)
);
ALTER TABLE eventHandlers
    ADD CONSTRAINT eventHandlersUn1 UNIQUE (xid);
ALTER TABLE eventHandlers
    ADD CONSTRAINT eventHandlersFk2 FOREIGN KEY (readPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;
ALTER TABLE eventHandlers
    ADD CONSTRAINT eventHandlersFk3 FOREIGN KEY (editPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;


CREATE TABLE eventHandlersMapping
(
    eventHandlerId   INT         NOT NULL,

    -- Event type, see events
    eventTypeName    VARCHAR(32) NOT NULL,
    eventSubtypeName VARCHAR(32) NOT NULL DEFAULT '',
    eventTypeRef1    INT         NOT NULL,
    eventTypeRef2    INT         NOT NULL
);
ALTER TABLE eventHandlersMapping
    ADD CONSTRAINT eventHandlersFk1 FOREIGN KEY (eventHandlerId) REFERENCES eventHandlers (id) ON DELETE CASCADE;
ALTER TABLE eventHandlersMapping
    ADD CONSTRAINT handlerMappingUniqueness UNIQUE (eventHandlerId, eventTypeName, eventSubtypeName, eventTypeRef1,
                                                    eventTypeRef2);

--
--
-- Audit Table
--
CREATE TABLE audit
(
    id         INT         NOT NULL AUTO_INCREMENT,
    typeName   VARCHAR(32) NOT NULL,
    alarmLevel INT         NOT NULL,
    userId     INT         NOT NULL,
    changeType INT         NOT NULL,
    objectId   INT         NOT NULL,
    ts         BIGINT      NOT NULL,
    context    LONGTEXT,
    message    VARCHAR(255),
    PRIMARY KEY (id)
);
CREATE INDEX tsIndex ON audit (ts ASC);
CREATE INDEX userIdIndex ON audit (userId ASC);
CREATE INDEX typeNameIndex ON audit (typeName ASC);
CREATE INDEX alarmLevelIndex ON audit (alarmLevel ASC);
--
--
-- Publishers
--
CREATE TABLE publishers
(
    id            INT          NOT NULL AUTO_INCREMENT,
    xid           VARCHAR(100) NOT NULL,
    publisherType VARCHAR(40)  NOT NULL,
    data          LONGBLOB     NOT NULL,
    rtdata        LONGBLOB,
    PRIMARY KEY (id)
);
ALTER TABLE publishers
    ADD CONSTRAINT publishersUn1 UNIQUE (xid);

--
--
-- JsonData
--
CREATE TABLE jsonData
(
    id               INT          NOT NULL AUTO_INCREMENT,
    xid              VARCHAR(100) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    data             LONGTEXT,
    readPermissionId INT          NOT NULL,
    editPermissionId INT          NOT NULL,
    PRIMARY KEY (id)
);
ALTER TABLE jsonData
    ADD CONSTRAINT jsonDataUn1 UNIQUE (xid);
ALTER TABLE jsonData
    ADD CONSTRAINT jsonDataFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;
ALTER TABLE jsonData
    ADD CONSTRAINT jsonDataFk2 FOREIGN KEY (editPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;

--
--
-- InstalledModules
--  Thirty character restriction is from the store
CREATE TABLE installedModules
(
    name    VARCHAR(30)  NOT NULL,
    version VARCHAR(255) NOT NULL
);
ALTER TABLE installedModules
    ADD CONSTRAINT installModulesUn1 UNIQUE (name);

--
--
-- FileStores
--
CREATE TABLE fileStores
(
    id                INT          NOT NULL AUTO_INCREMENT,
    xid               VARCHAR(100) NOT NULL,
    name              VARCHAR(255) NOT NULL,
    readPermissionId  INT          NOT NULL,
    writePermissionId INT          NOT NULL,
    PRIMARY KEY (id)
);
ALTER TABLE fileStores
    ADD CONSTRAINT fileStoresUn1 UNIQUE (xid);
ALTER TABLE fileStores
    ADD CONSTRAINT fileStoresFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;
ALTER TABLE fileStores
    ADD CONSTRAINT fileStoresFk2 FOREIGN KEY (writePermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;

--
--
-- Persistent session data
--
CREATE TABLE mangoSessionData
(
    sessionId      VARCHAR(120),
    contextPath    VARCHAR(60),
    virtualHost    VARCHAR(60),
    lastNode       VARCHAR(60),
    accessTime     BIGINT,
    lastAccessTime BIGINT,
    createTime     BIGINT,
    cookieTime     BIGINT,
    lastSavedTime  BIGINT,
    expiryTime     BIGINT,
    maxInterval    BIGINT,
    userId         INT,
    PRIMARY KEY (sessionId, contextPath, virtualHost)
);
CREATE INDEX mangoSessionDataExpiryIndex ON mangoSessionData (expiryTime);
CREATE INDEX mangoSessionDataSessionIndex ON mangoSessionData (sessionId, contextPath);
