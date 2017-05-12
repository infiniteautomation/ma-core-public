/**
 * Copyright (C) 2017 Infinite Automation Software. All rights reserved.
 *
 */
package com.serotonin.m2m2.module.definitions.settings;

import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.module.Module;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.module.ReadOnlySettingDefinition;

/**
 * Class to define Read only settings/information that can be provided
 * 
 * @author Terry Packer
 */
public class NoSqlDatabaseSizeSettingDefinition extends ReadOnlySettingDefinition<Long>{

	public final String KEY = "noSqlDatabaseSize";
	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.ReadOnlySettingDefinition#getName()
	 */
	@Override
	public String getKey() {
		return KEY;
	}

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.ReadOnlySettingDefinition#getValue()
	 */
	@Override
	public Long getValue() {
        long noSqlSize = 0L;
        if (Common.databaseProxy.getNoSQLProxy() != null) {
            noSqlSize = Common.databaseProxy.getNoSQLProxy().getDatabaseSizeInBytes();
        }
        return noSqlSize;
	}

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.ReadOnlySettingDefinition#getDescription()
	 */
	@Override
	public TranslatableMessage getDescription() {
		return new TranslatableMessage("systemSettings.noSqlDatabaseSize");
	}

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.ModuleElementDefinition#getModule()
	 */
	@Override
	public Module getModule() {
		return ModuleRegistry.getCoreModule();
	}

}
