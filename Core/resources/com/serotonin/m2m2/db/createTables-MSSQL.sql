--
--    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
--    @author Matthew Lohbihler
--
--
-- System settings
create table systemSettings (
  settingName nvarchar(64) not null,
  settingValue ntext,
  primary key (settingName)
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

--
-- Permissions
CREATE TABLE minterms (
	id int(11) NOT NULL AUTO_INCREMENT,
	PRIMARY KEY (id)
);

CREATE TABLE mintermsRoles (
	mintermId int(11) NOT NULL,
	roleId int(11) NOT NULL
);
ALTER TABLE mintermsRoles ADD CONSTRAINT mintermsRolesIdx1 UNIQUE (mintermId,roleId);
ALTER TABLE mintermsRoles ADD CONSTRAINT mintermsRolesFk1Idx KEY (mintermId);
ALTER TABLE mintermsRoles ADD CONSTRAINT mintermsRolesFk2_idx KEY (roleId);
ALTER TABLE mintermsRoles ADD CONSTRAINT mintermsRolesFk1 FOREIGN KEY (mintermId) REFERENCES minterms (id) ON DELETE CASCADE ON UPDATE NO ACTION;
ALTER TABLE mintermsRoles ADD CONSTRAINT mintermsRolesFk2 FOREIGN KEY (roleId) REFERENCES roles (id) ON DELETE CASCADE ON UPDATE NO ACTION;


CREATE TABLE permissions (
	id int(11) NOT NULL AUTO_INCREMENT,
	PRIMARY KEY (id)
);

CREATE TABLE permissionsMinterms (
	permissionId int(11) NOT NULL,
	mintermId int(11) NOT NULL
);
ALTER TABLE permissionsMinterms ADD CONSTRAINT permissionsMintermsIdx1 UNIQUE KEY (permissionId, mintermId);
ALTER TABLE permissionsMinterms ADD CONSTRAINT permissionsMintermsFk1Idx KEY (permissionId);
ALTER TABLE permissionsMinterms ADD CONSTRAINT permissionsMintermsFk2Idx KEY(mintermId);
ALTER TABLE permissionsMinterms ADD CONSTRAINT permissionsMintermsFk1 FOREIGN KEY (permissionId) REFERENCES permissions (id) ON DELETE CASCADE ON UPDATE NO ACTION;
ALTER TABLE permissionsMinterms ADD CONSTRAINT permissionsMintermsFk2 FOREIGN KEY (mintermId) REFERENCES minterms (id) ON DELETE CASCADE ON UPDATE NO ACTION;

--
-- System wide permissions
--
CREATE TABLE systemPermissions (
	permissionType NVARCHAR(255),
	permissionId INT NOT NULL
);
ALTER TABLE systemPermissions ADD CONSTRAINT systemPermissionsFk1 FOREIGN KEY (permissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE systemPermissions ADD CONSTRAINT permissionTypeUn1 UNIQUE(permissionType);

--
-- Users
create table users (
  id int not null identity,
  username nvarchar(40) not null,
  password nvarchar(255) not null,
  email nvarchar(255) not null,
  phone nvarchar(40),
  disabled char(1) not null,
  lastLogin bigint,
  homeUrl nvarchar(255),
  receiveAlarmEmails int not null,
  receiveOwnAuditEvents char(1) not null,
  timezone nvarchar(50),
  muted char(1),
  name nvarchar(255),
  locale nvarchar(50),
  tokenVersion int not null,
  passwordVersion int not null,
  passwordChangeTimestamp bigint NOT NULL,
  sessionExpirationOverride char(1),
  sessionExpirationPeriods int,
  sessionExpirationPeriodType nvarchar(25),
  organization nvarchar(80),
  organizationalRole nvarchar(80),
  createdTs bigint NOT NULL,
  emailVerifiedTs bigint,
  data ntext,
  readPermissionId int not null,
  editPermissionId int not null,
  primary key (id)
);
alter table users add constraint username_unique unique (username);
alter table users add constraint email_unique unique (email);
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


create table userComments (
  id int not null identity,
  xid nvarchar(100) not null,
  userId int,
  commentType int not null,
  typeKey int not null,
  ts bigint not null,
  commentText nvarchar(1024) not null
);
alter table userComments add constraint userCommentsFk1 foreign key (userId) references users(id);
alter table userComments add constraint userCommentsUn1 unique (xid);
CREATE INDEX userComments_performance1 ON userComments (commentType ASC, typeKey ASC);

--
-- Mailing lists
create table mailingLists (
  id int not null identity,
  xid nvarchar(100) not null,
  name nvarchar(40) not null,
  receiveAlarmEmails int not null,
  readPermissionId INT NOT NULL,
  editPermissionId INT NOT NULL,
  primary key (id)
);
alter table mailingLists add constraint mailingListsUn1 unique (xid);
ALTER TABLE mailingLists ADD CONSTRAINT mailingListsFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE mailingLists ADD CONSTRAINT mailingListsFk2 FOREIGN KEY (editPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;

create table mailingListInactive (
  mailingListId int not null,
  inactiveInterval int not null
);
alter table mailingListInactive add constraint mailingListInactiveFk1 foreign key (mailingListId)
  references mailingLists(id) on delete cascade;

create table mailingListMembers (
  mailingListId int not null,
  typeId int not null,
  userId int,
  address nvarchar(255)
);
alter table mailingListMembers add constraint mailingListMembersFk1 foreign key (mailingListId)
  references mailingLists(id) on delete cascade;


--
--
-- Data Sources
--
create table dataSources (
  id int not null identity,
  xid nvarchar(100) not null,
  name nvarchar(255) not null,
  dataSourceType nvarchar(40) not null,
  data image not null,
  jsonData ntext,
  rtdata image,
  readPermissionId INT NOT NULL,
  editPermissionId INT NOT NULL,
  primary key (id)
);
alter table dataSources add constraint dataSourcesUn1 unique (xid);
ALTER TABLE dataSources ADD CONSTRAINT dataSourcesFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE dataSources ADD CONSTRAINT dataSourcesFk2 FOREIGN KEY (editPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
CREATE INDEX nameIndex on dataSources (name ASC);

-- Time series table
CREATE TABLE timeSeries (
	id int NOT NULL IDENTITY,
	PRIMARY KEY (id)
);

--
--
-- Data Points
--
create table dataPoints (
  id int not null identity,
  xid nvarchar(100) not null,
  dataSourceId int not null,
  name nvarchar(255),
  deviceName nvarchar(255),
  enabled char(1),
  loggingType int,
  intervalLoggingPeriodType int,
  intervalLoggingPeriod int,
  intervalLoggingType int,
  tolerance float,
  purgeOverride char(1),
  purgeType int,
  purgePeriod int,
  defaultCacheSize int,
  discardExtremeValues char(1),
  engineeringUnits int,
  data image not null,
  rollup int,
  dataTypeId int not null,
  settable char(1),
  jsonData ntext,
  seriesId INT NOT NULL,
  readPermissionId INT NOT NULL,
  editPermissionId INT NOT NULL,
  setPermissionId INT NOT NULL,
  primary key (id)
);
alter table dataPoints add constraint dataPointsUn1 unique (xid);
alter table dataPoints add constraint dataPointsFk1 foreign key (dataSourceId) references dataSources(id);
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
create table pointValues (
  id bigint not null identity,
  dataPointId int not null,
  dataType int not null,
  pointValue float,
  ts bigint not null,
  primary key (id)
);
create index pointValuesIdx1 on pointValues (dataPointId, ts);

create table pointValueAnnotations (
  pointValueId bigint not null,
  textPointValueShort nvarchar(128),
  textPointValueLong ntext,
  sourceMessage ntext,
  primary key (pointValueId)
);

--
--
-- Event detectors
--
CREATE TABLE eventDetectors (
  id int NOT NULL identity,
  xid nvarchar(100) NOT NULL,
  sourceTypeName nvarchar(32) NOT NULL,
  typeName nvarchar(32) NOT NULL,
  dataPointId int,
  data ntext NOT NULL,
  jsonData ntext,
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
create table events (
  id int not null identity,
  typeName nvarchar(32) not null,
  subtypeName nvarchar(32),
  typeRef1 int not null,
  typeRef2 int not null,
  activeTs bigint not null,
  rtnApplicable char(1) not null,
  rtnTs bigint,
  rtnCause int,
  alarmLevel int not null,
  message ntext,
  ackTs bigint,
  ackUserId int,
  alternateAckSource ntext,
  readPermissionId INT NOT NULL,
  primary key (id)
);
alter table events add constraint eventsFk1 foreign key (ackUserId) references users(id);
ALTER TABLE events ADD CONSTRAINT eventsFk2 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
CREATE INDEX events_performance1 ON events (activeTs ASC);
CREATE INDEX events_performance2 ON events (rtnApplicable ASC, rtnTs ASC);
CREATE INDEX events_performance3 ON events (typeName ASC, subTypeName ASC, typeRef1 ASC);

--
--
-- Event handlers
--
create table eventHandlers (
  id int not null identity,
  xid nvarchar(100) not null,
  alias nvarchar(255) not null,
  eventHandlerType nvarchar(40) not null,
  readPermissionId INT NOT NULL,
  editPermissionId INT NOT NULL,

  data image not null,
  primary key (id)
);
alter table eventHandlers add constraint eventHandlersUn1 unique (xid);
ALTER TABLE eventHandlers ADD CONSTRAINT eventHandlersFk2 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE eventHandlers ADD CONSTRAINT eventHandlersFk3 FOREIGN KEY (editPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;

--
--
-- Audit Table
--
CREATE TABLE audit (
  id int NOT NULL identity,
  typeName nvarchar(32) NOT NULL,
  alarmLevel int NOT NULL,
  userId int NOT NULL,
  changeType int NOT NULL,
  objectId int NOT NULL,
  ts bigint NOT NULL,
  context ntext,
  message nvarchar(255),
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
create table publishers (
  id int not null identity,
  xid nvarchar(100) not null,
  publisherType nvarchar(40) not null,
  data image not null,
  rtdata image,
  primary key (id)
);
alter table publishers add constraint publishersUn1 unique (xid);

`
--
--
-- JsonData
--
CREATE TABLE jsonData (
  	id int not null identity,
	xid nvarchar(100) not null,
	name nvarchar(255) not null,
  	data ntext,
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
	name nvarchar(30) not null,
	version nvarchar(255) not null
);
ALTER TABLE installedModules ADD CONSTRAINT installModulesUn1 UNIQUE (name);

--
--
-- FileStores
--
CREATE TABLE fileStores (
	id int not null auto_increment,
    xid nvarchar(100) not null,
    name nvarchar(255) not null,
	readPermissionId INT NOT NULL,
    writePermissionId INT NOT NULL,
	PRIMARY KEY (id)
);
ALTER TABLE fileStores ADD CONSTRAINT fileStoresUn1 UNIQUE (xid);
ALTER TABLE fileStores ADD CONSTRAINT fileStoresFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE fileStores ADD CONSTRAINT fileStoresFk2 FOREIGN KEY (writePermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;

--
--
-- Persistent session data
--
CREATE TABLE mangoSessionData (
	sessionId NVARCHAR(120),
	contextPath NVARCHAR(60),
	virtualHost NVARCHAR(60),
	lastNode NVARCHAR(60),
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
