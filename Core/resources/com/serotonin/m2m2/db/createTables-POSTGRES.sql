--
--    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
--    @author Matthew Lohbihler
--

--
-- System settings
CREATE TABLE systemSettings (
  settingName varchar(64) NOT NULL,
  settingValue text,
  PRIMARY KEY (settingName)
);

--
--
-- Roles
--
CREATE TABLE roles (
	id int not null auto_increment,
	xid varchar(100) not null,
	name varchar(255) not null,
  	primary key (id)
);
ALTER TABLE roles ADD CONSTRAINT rolesUn1 UNIQUE (xid);

--
-- Role Inheritance Mappings
--
CREATE TABLE roleInheritance (
	roleId INT NOT NULL,
	inheritedRoleId INT NOT NULL
);
ALTER TABLE roleInheritance ADD CONSTRAINT roleInheritanceUn1 UNIQUE (roleId,inheritedRoleId);
ALTER TABLE roleInheritance ADD CONSTRAINT roleInheritanceFk1 FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE;
ALTER TABLE roleInheritance ADD CONSTRAINT roleInheritanceFk2 FOREIGN KEY (inheritedRoleId) REFERENCES roles(id) ON DELETE CASCADE;

CREATE TABLE minterms (
  id int(11) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (id)
);

CREATE TABLE mintermsRoles (
  mintermId int(11) NOT NULL,
  roleId int(11) NOT NULL,
  UNIQUE KEY mintermsRolesIdx1 (mintermId, roleId),
  KEY mintermsRolesFk1Idx (mintermId),
  KEY mintermsRolesFk2Idx (roleId),
  CONSTRAINT mintermsRolesFk1 FOREIGN KEY (mintermId) REFERENCES minterms (id) ON DELETE CASCADE ON UPDATE NO ACTION,
  CONSTRAINT mintermsRolesFk2 FOREIGN KEY (roleId) REFERENCES roles (id) ON DELETE CASCADE ON UPDATE NO ACTION
);

CREATE TABLE permissions (
  id int(11) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (id)
);

CREATE TABLE permissionsMinterms (
  permissionId int(11) NOT NULL,
  mintermId int(11) NOT NULL,
  UNIQUE KEY permissionsMintermsIdx1 (permissionId, mintermId),
  KEY permissionsMintermsFk1Idx (permissionId),
  KEY permissionsMintermsFk2Idx (mintermId),
  CONSTRAINT permissionsMintermsFk1 FOREIGN KEY (permissionId) REFERENCES permissions (id) ON DELETE CASCADE ON UPDATE NO ACTION,
  CONSTRAINT permissionsMintermsFk2 FOREIGN KEY (mintermId) REFERENCES minterms (id) ON DELETE CASCADE ON UPDATE NO ACTION
);

--
-- System wide permissions
--
CREATE TABLE systemPermissions (
	id int(11) NOT NULL AUTO_INCREMENT,
	permissionType VARCHAR(255),
	permissionId INT NOT NULL,
	PRIMARY KEY (id)
);
ALTER TABLE systemPermissions ADD CONSTRAINT systemPermissionsFk1 FOREIGN KEY (permissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE systemPermissions ADD CONSTRAINT permissionTypeUn1 UNIQUE(permissionType);

--
-- Users
CREATE TABLE users (
  id SERIAL,
  username varchar(40) NOT NULL,
  password varchar(255) NOT NULL,
  email varchar(255) NOT NULL,
  phone varchar(40),
  disabled character(1) NOT NULL,
  lastLogin bigint,
  homeUrl varchar(255),
  receiveAlarmEmails integer NOT NULL,
  receiveOwnAuditEvents character(1) NOT NULL,
  timezone varchar(50),
  muted character(1),
  permissions varchar(255),
  name varchar(255),
  locale varchar(50),
  tokenVersion integer NOT NULL,
  passwordVersion integer NOT NULL,
  passwordChangeTimestamp bigint NOT NULL,
  sessionExpirationOverride char(1),
  sessionExpirationPeriods int,
  sessionExpirationPeriodType varchar(25),
  organization varchar(80),
  organizationalRole varchar(80),
  createdTs bigint NOT NULL,
  emailVerifiedTs bigint,
  data JSON,
  readPermissionId int not null,
  editPermissionId int not null,
  PRIMARY KEY (id)
);
ALTER TABLE users ADD CONSTRAINT username_unique UNIQUE (username);
ALTER TABLE users ADD CONSTRAINT email_unique UNIQUE (email);
ALTER TABLE users ADD CONSTRAINT usersFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;
ALTER TABLE users ADD CONSTRAINT usersFk2 FOREIGN KEY (editPermissionId) REFERENCES permissions (id) ON DELETE RESTRICT;

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
CREATE TABLE userRoleMappings (
	roleId int not null,
	userId int not null
);
ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsFk1 FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE;
ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsFk2 FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsUn1 UNIQUE (roleId,userId);

CREATE TABLE userComments (
  id int not null auto_increment,
  xid varchar(100) NOT NULL,
  userId integer,
  commentType integer NOT NULL,
  typeKey integer NOT NULL,
  ts bigint NOT NULL,
  commentText varchar(1024) NOT NULL
);
ALTER TABLE userComments ADD CONSTRAINT userCommentsFk1 FOREIGN KEY (userId) REFERENCES users(id);
ALTER TABLE userComments ADD CONSTRAINT userCommentsUn1 UNIQUE (xid);
ALTER TABLE userComments ADD INDEX userComments_performance1 (`commentType` ASC, `typeKey` ASC);

--
-- Mailing lists
CREATE TABLE mailingLists (
  id SERIAL,
  xid varchar(100) NOT NULL,
  name varchar(40) NOT NULL,
  receiveAlarmEmails INT NOT NULL,
  readPermissionId INT NOT NULL,
  editPermissionId INT NOT NULL,
  PRIMARY KEY (id)
);
ALTER TABLE mailingLists ADD CONSTRAINT mailingListsUn1 UNIQUE (xid);
ALTER TABLE mailingLists ADD CONSTRAINT mailingListsFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE mailingLists ADD CONSTRAINT mailingListsFk2 FOREIGN KEY (editPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;

CREATE TABLE mailingListInactive (
  mailingListId integer NOT NULL,
  inactiveInterval integer NOT NULL
);
ALTER TABLE mailingListInactive ADD CONSTRAINT mailingListInactiveFk1 FOREIGN KEY (mailingListId)
  REFERENCES mailingLists(id) ON DELETE CASCADE;

CREATE TABLE mailingListMembers (
  mailingListId integer NOT NULL,
  typeId integer NOT NULL,
  userId integer,
  address varchar(255)
);
ALTER TABLE mailingListMembers ADD CONSTRAINT mailingListMembersFk1 FOREIGN KEY (mailingListId)
  REFERENCES mailingLists(id) ON DELETE CASCADE;

--
--
-- Data Sources
--
CREATE TABLE dataSources (
  id SERIAL,
  xid varchar(100) NOT NULL,
  name varchar(255) NOT NULL,
  dataSourceType varchar(40) NOT NULL,
  data bytea NOT NULL,
  jsonData json,
  rtdata bytea,
  readPermissionId INT NOT NULL,
  editPermissionId INT NOT NULL,
  PRIMARY KEY (id)
);
ALTER TABLE dataSources ADD CONSTRAINT dataSourcesUn1 UNIQUE (xid);
ALTER TABLE dataSources ADD CONSTRAINT dataSourcesFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE dataSources ADD CONSTRAINT dataSourcesFk2 FOREIGN KEY (editPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
CREATE INDEX nameIndex on dataSources (name ASC);

-- Time series table
CREATE TABLE timeSeries (
	id int NOT NULL auto_increment,
	PRIMARY KEY (id)
);

--
--
-- Data Points
--
CREATE TABLE dataPoints (
  id SERIAL,
  xid varchar(100) NOT NULL,
  dataSourceId integer NOT NULL,
  name varchar(255),
  deviceName varchar(255),
  enabled character(1),
  loggingType integer,
  intervalLoggingPeriodType integer,
  intervalLoggingPeriod integer,
  intervalLoggingType integer,
  tolerance double precision,
  purgeOverride character(1),
  purgeType integer,
  purgePeriod integer,
  defaultCacheSize integer,
  discardExtremeValues character(1),
  engineeringUnits integer,
  data bytea NOT NULL,
  templateId int,
  rollup int,
  dataTypeId int not null,
  settable char(1),
  jsonData json,
  seriesId INT NOT NULL,
  readPermissionId INT NOT NULL,
  editPermissionId INT NOT NULL,
  setPermissionId INT NOT NULL,
  PRIMARY KEY (id)
);
ALTER TABLE dataPoints ADD CONSTRAINT dataPointsUn1 UNIQUE (xid);
ALTER TABLE dataPoints ADD CONSTRAINT dataPointsFk1 FOREIGN KEY (dataSourceId) REFERENCES dataSources(id);
ALTER TABLE dataPoints ADD CONSTRAINT dataPointsFk2 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE dataPoints ADD CONSTRAINT dataPointsFk3 FOREIGN KEY (editPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE dataPoints ADD CONSTRAINT dataPointsFk4 FOREIGN KEY (setPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE dataPoints ADD CONSTRAINT dataPointsFk5 FOREIGN KEY (seriesId) REFERENCES timeSeries(id);

CREATE INDEX pointNameIndex on dataPoints (name ASC);
CREATE INDEX deviceNameNameIdIndex ON dataPoints (deviceName ASC, name ASC, id ASC);
CREATE INDEX enabledIndex on dataPoints (enabled ASC);
CREATE INDEX xidNameIndex on dataPoints (xid ASC, name ASC);
CREATE INDEX dataSourceIdFkIndex ON dataPoints (dataSourceId ASC);

-- Data point tags
CREATE TABLE dataPointTags (
  dataPointId INT NOT NULL,
  tagKey VARCHAR(255) NOT NULL,
  tagValue VARCHAR(255) NOT NULL,
  PRIMARY KEY (dataPointId, tagKey)
);
ALTER TABLE dataPointTags ADD CONSTRAINT dataPointTagsFk1 FOREIGN KEY (dataPointId) REFERENCES dataPoints (id) ON DELETE CASCADE;
CREATE INDEX dataPointTagsIndex1 ON dataPointTags (tagKey ASC, tagValue ASC);

--
--
-- Point Values (historical data)
--  the dataPointId column refers to the series id of the data point, 
--  which is not necessarily the data point id
--
CREATE TABLE pointValues (
  id BIGSERIAL,
  dataPointId integer NOT NULL,
  dataType integer NOT NULL,
  pointValue double precision,
  ts bigint NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX pointValuesIdx1 ON pointValues (dataPointId, ts);

CREATE TABLE pointValueAnnotations (
  pointValueId bigint NOT NULL,
  textPointValueShort varchar(128),
  textPointValueLong text,
  sourceMessage text,
  PRIMARY KEY (pointValueId)
);

--
--
-- Event detectors
--
CREATE TABLE eventDetectors (
  id SERIAL,
  xid varchar(100) NOT NULL,
  sourceTypeName varchar(32) NOT NULL,
  typeName varchar(32) NOT NULL,
  dataPointId int NOT NULL,
  data longtext NOT NULL,
  jsonData JSON,
  readPermissionId INT NOT NULL,
  editPermissionId INT NOT NULL,
  PRIMARY KEY (id)
);
ALTER TABLE eventDetectors ADD CONSTRAINT eventDetectorsUn1 UNIQUE (xid);
ALTER TABLE eventDetectors ADD CONSTRAINT dataPointIdFk FOREIGN KEY (dataPointId) REFERENCES dataPoints(id);
ALTER TABLE eventDetectors ADD CONSTRAINT eventDetectorsFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE eventDetectors ADD CONSTRAINT eventDetectorsFk2 FOREIGN KEY (editPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;

--
--
-- Events
--
CREATE TABLE events (
  id SERIAL,
  typeName varchar(32) NOT NULL,
  subtypeName varchar(32),
  typeRef1 integer NOT NULL,
  typeRef2 integer NOT NULL,
  activeTs bigint NOT NULL,
  rtnApplicable character(1) NOT NULL,
  rtnTs bigint,
  rtnCause integer,
  alarmLevel integer NOT NULL,
  message text,
  ackTs bigint,
  ackUserId integer,
  alternateAckSource text,
  readPermissionId INT NOT NULL,
  PRIMARY KEY (id)
);
ALTER TABLE events ADD CONSTRAINT eventsFk1 FOREIGN KEY (ackUserId) REFERENCES users(id);
ALTER TABLE events ADD CONSTRAINT eventsFk2 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
alter table events add index performance1 (activeTs ASC);
ALTER TABLE events ADD INDEX events_performance2 (`rtnApplicable` ASC, `rtnTs` ASC);
ALTER TABLE events ADD INDEX events_performance3 (`typeName` ASC, `subTypeName` ASC, `typeRef1` ASC);

--
--
-- Event handlers
--
CREATE TABLE eventHandlers (
  id SERIAL,
  xid varchar(100) NOT NULL,
  alias varchar(255) NOT NULL,
  eventHandlerType varchar(40) NOT NULL,
  readPermissionId INT NOT NULL,
  editPermissionId INT NOT NULL,
  data bytea NOT NULL,
  PRIMARY KEY (id)
);
ALTER TABLE eventHandlers ADD CONSTRAINT eventHandlersUn1 UNIQUE (xid);
ALTER TABLE eventHandlers ADD CONSTRAINT eventHandlersFk2 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE eventHandlers ADD CONSTRAINT eventHandlersFk3 FOREIGN KEY (editPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;

--
--
-- Audit Table
--
CREATE TABLE audit (
  id SERIAL,
  typeName varchar(32) NOT NULL,
  alarmLevel int NOT NULL,
  userId int NOT NULL,
  changeType int NOT NULL,
  objectId int NOT NULL,
  ts bigint NOT NULL,
  context longtext,
  message varchar(255),
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
CREATE TABLE publishers (
  id SERIAL,
  xid varchar(100) NOT NULL,
  publisherType varchar(40) NOT NULL,
  data bytea NOT NULL,
  rtdata bytea,
  PRIMARY KEY (id)
);
ALTER TABLE publishers ADD CONSTRAINT publishersUn1 UNIQUE (xid);

CREATE TABLE jsonData (
  	id SERIAL,
	xid varchar(100) not null,
	name varchar(255) not null,
  	data clob,
  	readPermissionId INT NOT NULL,
    editPermissionId INT NOT NULL,
    primary key (id)
);
ALTER TABLE jsonData ADD CONSTRAINT jsonDataUn1 UNIQUE (xid);
ALTER TABLE jsonData ADD CONSTRAINT jsonDataFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE jsonData ADD CONSTRAINT jsonDataFk2 FOREIGN KEY (editPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;

--
--
-- InstalledModules
--  Thirty character restriction is from the store
CREATE TABLE installedModules (
	name varchar(30) not null,
	version varchar(255) not null
);
ALTER TABLE installedModules ADD CONSTRAINT installModulesUn1 UNIQUE (name);

--
--
-- FileStores
--
CREATE TABLE fileStores (
	id int not null auto_increment,
	xid varchar(100) not null,
    name varchar(255) not null,
	readPermissionId INT NOT NULL,
    writePermissionId INT NOT NULL,
	PRIMARY KEY (id)
);
ALTER TABLE fileStores ADD CONSTRAINT fileStoresUn1 UNIQUE (xid);
ALTER TABLE fileStores ADD CONSTRAINT fileStoresFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE fileStores ADD CONSTRAINT fileStoresFk2 FOREIGN KEY (writePermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;

--
--
-- User Role Mappings
--
CREATE TABLE userRoleMappings (
	roleId int not null,
	userId int not null
);
ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsFk1 FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE;
ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsFk2 FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsUn1 UNIQUE (roleId,userId);

--
--
-- Persistent session data
--
CREATE TABLE mangoSessionData (
	sessionId VARCHAR(120),
	contextPath VARCHAR(60),
	virtualHost VARCHAR(60),
	lastNode VARCHAR(60),
	accessTime BIGINT,
	lastAccessTime BIGINT,
	createTime BIGINT,
	cookieTime BIGINT,
	lastSavedTime BIGINT,
	expiryTime BIGINT,
	maxInterval BIGINT,
	userId INT,
	primary key (sessionId, contextPath, virtualHost)
);
CREATE INDEX mangoSessionDataExpiryIndex ON mangoSessionData (expiryTime);
CREATE INDEX mangoSessionDataSessionIndex ON mangoSessionData (sessionId, contextPath);
