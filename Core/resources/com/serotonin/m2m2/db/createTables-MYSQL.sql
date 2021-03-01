--
--    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
--    @author Matthew Lohbihler
--

-- Make sure that everything get created with utf8mb4 as the charset.
-- Collation is full unicode comparison, case-insensitive
alter database default character set utf8mb4 collate utf8mb4_unicode_ci;

--
-- System settings
create table systemSettings (
  settingName varchar(64) not null,
  settingValue longtext,
  primary key (settingName)
) engine=InnoDB;


--
--
-- Roles
--
CREATE TABLE roles (
	id int not null auto_increment,
	xid varchar(100) not null,
	name varchar(255) not null,
  	primary key (id)
) engine=InnoDB;
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
) ENGINE=InnoDB;

CREATE TABLE mintermsRoles (
  mintermId int(11) NOT NULL,
  roleId int(11) NOT NULL,
  UNIQUE KEY mintermsRolesIdx1 (mintermId, roleId),
  KEY mintermsRolesFk1Idx (mintermId),
  KEY mintermsRolesFk2Idx (roleId),
  CONSTRAINT mintermsRolesFk1 FOREIGN KEY (mintermId) REFERENCES minterms (id) ON DELETE CASCADE ON UPDATE NO ACTION,
  CONSTRAINT mintermsRolesFk2 FOREIGN KEY (roleId) REFERENCES roles (id) ON DELETE CASCADE ON UPDATE NO ACTION
) ENGINE=InnoDB;

CREATE TABLE permissions (
  id int(11) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE permissionsMinterms (
  permissionId int(11) NOT NULL,
  mintermId int(11) NOT NULL,
  UNIQUE KEY permissionsMintermsIdx1 (permissionId, mintermId),
  KEY permissionsMintermsFk1Idx (permissionId),
  KEY permissionsMintermsFk2Idx (mintermId),
  CONSTRAINT permissionsMintermsFk1 FOREIGN KEY (permissionId) REFERENCES permissions (id) ON DELETE CASCADE ON UPDATE NO ACTION,
  CONSTRAINT permissionsMintermsFk2 FOREIGN KEY (mintermId) REFERENCES minterms (id) ON DELETE CASCADE ON UPDATE NO ACTION
) ENGINE=InnoDB;

--
-- System wide permissions
--
CREATE TABLE systemPermissions (
	permissionType VARCHAR(255),
	permissionId INT NOT NULL
)ENGINE=InnoDB;
ALTER TABLE systemPermissions ADD CONSTRAINT systemPermissionsFk1 FOREIGN KEY (permissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE systemPermissions ADD CONSTRAINT permissionTypeUn1 UNIQUE(permissionType);

--
-- Users
create table users (
  id int not null auto_increment,
  username varchar(40) not null,
  password varchar(255) not null,
  email varchar(255) not null,
  phone varchar(40),
  disabled char(1) not null,
  lastLogin bigint,
  homeUrl varchar(255),
  receiveAlarmEmails int not null,
  receiveOwnAuditEvents char(1) not null,
  timezone varchar(50),
  muted char(1),
  name varchar(255),
  locale varchar(50),
  tokenVersion int not null,
  passwordVersion int not null,
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
  primary key (id)
) engine=InnoDB;
ALTER TABLE users ADD CONSTRAINT username_unique UNIQUE(username);
ALTER TABLE users ADD CONSTRAINT email_unique UNIQUE(email);
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
) engine=InnoDB;
ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsFk1 FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE;
ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsFk2 FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsUn1 UNIQUE (roleId,userId);


create table userComments (
  id int not null auto_increment,
  xid varchar(100) not null,
  userId int,
  commentType int not null,
  typeKey int not null,
  ts bigint not null,
  commentText varchar(1024) not null,
  primary key (id)
) engine=InnoDB;
alter table userComments add constraint userCommentsFk1 foreign key (userId) references users(id);
alter table userComments add constraint userCommentsUn1 unique (xid);
ALTER TABLE userComments ADD INDEX userComments_performance1 (`commentType` ASC, `typeKey` ASC);

--
-- Mailing lists
create table mailingLists (
  id int not null auto_increment,
  xid varchar(100) not null,
  name varchar(40) not null,
  receiveAlarmEmails int not null,
  readPermissionId INT NOT NULL,
  editPermissionId INT NOT NULL,
  primary key (id)
) engine=InnoDB;
alter table mailingLists add constraint mailingListsUn1 unique (xid);
ALTER TABLE mailingLists ADD CONSTRAINT mailingListsFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE mailingLists ADD CONSTRAINT mailingListsFk2 FOREIGN KEY (editPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;

create table mailingListInactive (
  mailingListId int not null,
  inactiveInterval int not null
) engine=InnoDB;
alter table mailingListInactive add constraint mailingListInactiveFk1 foreign key (mailingListId)
  references mailingLists(id) on delete cascade;

create table mailingListMembers (
  mailingListId int not null,
  typeId int not null,
  userId int,
  address varchar(255)
) engine=InnoDB;
alter table mailingListMembers add constraint mailingListMembersFk1 foreign key (mailingListId)
  references mailingLists(id) on delete cascade;

--
--
-- Data Sources
--
create table dataSources (
  id int not null auto_increment,
  xid varchar(100) not null,
  name varchar(255) not null,
  dataSourceType varchar(40) not null,
  data longblob not null,
  jsonData JSON,
  rtdata longblob,
  readPermissionId INT NOT NULL,
  editPermissionId INT NOT NULL,
  primary key (id)
) engine=InnoDB;
alter table dataSources add constraint dataSourcesUn1 unique (xid);
ALTER TABLE dataSources ADD CONSTRAINT dataSourcesFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE dataSources ADD CONSTRAINT dataSourcesFk2 FOREIGN KEY (editPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE dataSources ADD INDEX nameIndex (name ASC);

-- Time series table
CREATE TABLE timeSeries (
	id int NOT NULL auto_increment,
	PRIMARY KEY (id)
) engine=InnoDB;

--
--
-- Data Points
--
CREATE TABLE dataPoints (
  id int not null auto_increment,
  xid varchar(100) not null,
  dataSourceId int not null,
  name varchar(255),
  deviceName varchar(255),
  enabled char(1),
  loggingType int,
  intervalLoggingPeriodType int,
  intervalLoggingPeriod int,
  intervalLoggingType int,
  tolerance double,
  purgeOverride char(1),
  purgeType int,
  purgePeriod int,
  defaultCacheSize int,
  discardExtremeValues char(1),
  engineeringUnits int,
  data longblob not null,
  templateId int,
  rollup int,
  dataTypeId int not null,
  settable char(1),
  jsonData JSON,
  seriesId INT NOT NULL,
  readPermissionId INT NOT NULL,
  editPermissionId INT NOT NULL,
  setPermissionId INT NOT NULL,
  primary key (id)
) engine=InnoDB;
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

-- Data point tags
CREATE TABLE dataPointTags (
  dataPointId INT NOT NULL,
  tagKey VARCHAR(255) NOT NULL,
  tagValue VARCHAR(255) NOT NULL
) engine=InnoDB;
ALTER TABLE dataPointTags ADD CONSTRAINT dataPointTagsUn1 UNIQUE (dataPointId ASC, tagKey ASC);
ALTER TABLE dataPointTags ADD CONSTRAINT dataPointTagsFk1 FOREIGN KEY (dataPointId) REFERENCES dataPoints (id) ON DELETE CASCADE;
CREATE INDEX dataPointTagsIndex1 ON dataPointTags (tagKey ASC, tagValue ASC);


--
--
-- Point Values (historical data)
--  the dataPointId column refers to the series id of the data point, 
--  which is not necessarily the data point id
--
create table pointValues (
  id bigint not null auto_increment,
  dataPointId int not null,
  dataType int not null,
  pointValue double,
  ts bigint not null,
  primary key (id)
) engine=InnoDB;
create index pointValuesIdx1 on pointValues (dataPointId, ts);

create table pointValueAnnotations (
  pointValueId bigint not null,
  textPointValueShort varchar(128),
  textPointValueLong longtext,
  sourceMessage longtext,
  primary key (pointValueId)
) engine=InnoDB;

--
--
-- Event detectors
--
CREATE TABLE eventDetectors (
  id int NOT NULL auto_increment,
  xid varchar(100) NOT NULL,
  sourceTypeName varchar(32) NOT NULL,
  typeName varchar(32) NOT NULL,
  dataPointId int,
  data longtext NOT NULL,
  jsonData JSON,
  readPermissionId INT NOT NULL,
  editPermissionId INT NOT NULL,
  PRIMARY KEY (id)
)engine=InnoDB;
ALTER TABLE eventDetectors ADD CONSTRAINT eventDetectorsUn1 UNIQUE (xid);
ALTER TABLE eventDetectors ADD CONSTRAINT dataPointIdFk FOREIGN KEY (dataPointId) REFERENCES dataPoints(id);
ALTER TABLE eventDetectors ADD CONSTRAINT eventDetectorsFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE eventDetectors ADD CONSTRAINT eventDetectorsFk2 FOREIGN KEY (editPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;

--
--
-- Events
--
create table events (
  id int not null auto_increment,
  typeName varchar(32) not null,
  subtypeName varchar(32),
  typeRef1 int not null,
  typeRef2 int not null,
  activeTs bigint not null,
  rtnApplicable char(1) not null,
  rtnTs bigint,
  rtnCause int,
  alarmLevel int not null,
  message longtext,
  ackTs bigint,
  ackUserId int,
  alternateAckSource longtext,
  readPermissionId INT NOT NULL,
  primary key (id)
) engine=InnoDB;
alter table events add constraint eventsFk1 foreign key (ackUserId) references users(id);
ALTER TABLE events ADD CONSTRAINT eventsFk2 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
alter table events add index performance1 (activeTs ASC);
ALTER TABLE events ADD INDEX events_performance2 (`rtnApplicable` ASC, `rtnTs` ASC);
ALTER TABLE events ADD INDEX events_performance3 (`typeName` ASC, `subTypeName` ASC, `typeRef1` ASC);

--
--
-- Event handlers
--
create table eventHandlers (
  id int not null auto_increment,
  xid varchar(100) not null,
  alias varchar(255) not null,
  eventHandlerType varchar(40) NOT NULL,
  readPermissionId INT NOT NULL,
  editPermissionId INT NOT NULL,
  data longblob not null,
  primary key (id)
) engine=InnoDB;
alter table eventHandlers add constraint eventHandlersUn1 unique (xid);
ALTER TABLE eventHandlers ADD CONSTRAINT eventHandlersFk2 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE eventHandlers ADD CONSTRAINT eventHandlersFk3 FOREIGN KEY (editPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;

CREATE TABLE eventHandlersMapping (
  eventHandlerId int not null,

  -- Event type, see events
  eventTypeName varchar(32) NOT NULL,
  eventSubtypeName varchar(32) NOT NULL DEFAULT '',
  eventTypeRef1 int NOT NULL,
  eventTypeRef2 int NOT NULL
)engine=InnoDB;
ALTER TABLE eventHandlersMapping ADD CONSTRAINT eventHandlersFk1 FOREIGN KEY (eventHandlerId) REFERENCES eventHandlers(id) ON DELETE CASCADE;
ALTER TABLE eventHandlersMapping ADD CONSTRAINT handlerMappingUniqueness UNIQUE(eventHandlerId, eventTypeName, eventSubtypeName, eventTypeRef1, eventTypeRef2);

--
--
-- Audit Table
--
CREATE TABLE audit (
  id int NOT NULL auto_increment,
  typeName varchar(32) NOT NULL,
  alarmLevel int NOT NULL,
  userId int NOT NULL,
  changeType int NOT NULL,
  objectId int NOT NULL,
  ts bigint NOT NULL,
  context longtext,
  message varchar(255),
  PRIMARY KEY (id)
)engine=InnoDB;
CREATE INDEX tsIndex ON audit (ts ASC);
CREATE INDEX userIdIndex ON audit (userId ASC);
CREATE INDEX typeNameIndex ON audit (typeName ASC);
CREATE INDEX alarmLevelIndex ON audit (alarmLevel ASC);

--
--
-- Publishers
--
create table publishers (
  id int not null auto_increment,
  xid varchar(100) not null,
  publisherType varchar(40) not null,
  data longblob not null,
  rtdata longblob,
  primary key (id)
) engine=InnoDB;
alter table publishers add constraint publishersUn1 unique (xid);

--
--
-- JsonData
--
CREATE TABLE jsonData (
  	id int not null auto_increment,
	xid varchar(100) not null,
	name varchar(255) not null,
  	data longtext,
  	readPermissionId INT NOT NULL,
    editPermissionId INT NOT NULL,
    primary key (id)
) engine=InnoDB;
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
) engine=InnoDB;
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
) engine=InnoDB;
ALTER TABLE fileStores ADD CONSTRAINT fileStoresUn1 UNIQUE (xid);
ALTER TABLE fileStores ADD CONSTRAINT fileStoresFk1 FOREIGN KEY (readPermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;
ALTER TABLE fileStores ADD CONSTRAINT fileStoresFk2 FOREIGN KEY (writePermissionId) REFERENCES permissions(id) ON DELETE RESTRICT;

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
)engine=InnoDB;
CREATE INDEX mangoSessionDataExpiryIndex ON mangoSessionData (expiryTime);
CREATE INDEX mangoSessionDataSessionIndex ON mangoSessionData (sessionId, contextPath);
