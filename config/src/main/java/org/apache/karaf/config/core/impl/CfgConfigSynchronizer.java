/*
 * This file and its content is Copyright of A-to-Be - Mobility Technology, S.A.
 * 2001-2021 All rights reserved.
 *
 * This software is the confidential and proprietary information of A-to-Be. You may
 * not disclose such Confidential Information and shall use it only in accordance with
 * the terms of the license agreement you entered into with A-to-Be.
 */
package org.apache.karaf.config.core.impl;

import java.io.File;
import java.io.IOException;
import java.util.Dictionary;

import org.apache.felix.fileinstall.internal.DirectoryWatcher;
import org.apache.felix.utils.collections.DictionaryAsMap;
import org.apache.felix.utils.properties.TypedProperties;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Attempts to ensure that configurations created using other means than file or config:edit, are replicated as a cfg file in
 * ${karaf.etc} directory. This is mainly the case for configurations created via web console.
 * 
 * This service listens for configuration changes and when a configuration lacking the felix.fileinstall.filename property is detected, it will
 * create a cfg file with the configuration properties and then add a felix.fileinstall.filename property.  
 *
 */
public class CfgConfigSynchronizer implements ConfigurationListener {

    private final static Logger LOGGER = LoggerFactory.getLogger(CfgConfigSynchronizer.class);

    private ConfigurationAdmin configurationAdmin;

    public CfgConfigSynchronizer(ConfigurationAdmin configurationAdmin) {

        this.configurationAdmin = configurationAdmin;
    }

    @Override
    public void configurationEvent(ConfigurationEvent event) {

        if (event.getType() == ConfigurationEvent.CM_UPDATED) {
            try {
                Configuration configuration = configurationAdmin.getConfiguration(event.getPid(), null);
                Dictionary<String, Object> properties = configuration.getProperties();                                
                String fileName = properties != null ? (String) properties.get( DirectoryWatcher.FILENAME ) : null;                               
                if (fileName == null  && properties != null) {                    
                    File file = generateConfigFilename(configuration, "cfg");
                    LOGGER.debug("Replicating config to {}", file);
                    TypedProperties props = new TypedProperties();
                    props.putAll(new DictionaryAsMap<>(properties));
                    props.remove(Constants.SERVICE_PID);
                    props.remove(ConfigurationAdmin.SERVICE_FACTORYPID);
                    props.save(file);
                    properties.put(DirectoryWatcher.FILENAME, file.toURI().toString());
                    configuration.update(properties);
                }
            }
            catch (IOException ex) {
                LOGGER.warn("Can't update configuration", ex);
            }

        }
    }

    private static File generateConfigFilename(Configuration cfg, String suffix) {

        final String pid = cfg.getPid();
        final String factoryPid = cfg.getFactoryPid();
        String fName;
        if (factoryPid != null) {
            // pid = <factoryPid>.<identifier>
            String identifier = pid.substring(factoryPid.length() + 1);
            fName = cfg.getFactoryPid() + "-" + identifier + "." + suffix;
        }
        else {
            fName = pid + "." + suffix;
        }
        return new File(System.getProperty("karaf.etc"), fName);
    }

}
