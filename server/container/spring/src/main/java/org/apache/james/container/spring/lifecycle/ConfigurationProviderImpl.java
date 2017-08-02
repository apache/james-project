/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.container.spring.lifecycle;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Register Configuration and act as Configuration Provider.
 */
public class ConfigurationProviderImpl implements ConfigurationProvider, ResourceLoaderAware, InitializingBean {
    private static final String CONFIGURATION_FILE_SUFFIX = ".xml";

    /**
     * A map of loaded configuration per bean.
     */
    private final Map<String, HierarchicalConfiguration> configurations = new HashMap<>();

    /**
     * Mappings for bean names associated with their related
     * "resourceName.configPart" pattern.<br>
     * The resourceName is the XML configuration file name, the configPart is
     * the tag within the XML to look for.
     */
    private Map<String, String> configurationMappings;

    /**
     * The Spring Resource Loader. Injected via setResourceLoader because this
     * class implements ResourceLoaderAware.
     */
    private ResourceLoader loader;

    /**
     * Inject the needed configuration mappings.
     * 
     * @param configurationMappings
     */
    public void setConfigurationMappings(Map<String, String> configurationMappings) {
        this.configurationMappings = configurationMappings;
    }

    /**
     * @see ConfigurationProvider#registerConfiguration(String, HierarchicalConfiguration)
     */
    public void registerConfiguration(String beanName, HierarchicalConfiguration conf) {
        configurations.put(beanName, conf);
    }

    /**
     * Responsible to register additional configurations for the injected
     * configurationMappings.
     * 
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    public void afterPropertiesSet() throws Exception {
        if (configurationMappings != null) {
            for (String key : configurationMappings.keySet()) {
                String value = configurationMappings.get(key);
                registerConfiguration(key, getConfiguration(value));
            }
        }
    }

    /**
     * @see ConfigurationProvider#getConfiguration(java.lang.String)
     */
    public HierarchicalConfiguration getConfiguration(String name) throws ConfigurationException {

        HierarchicalConfiguration conf = configurations.get(name);

        // Simply return the configuration if it is already loaded.
        if (conf != null) {
            return conf;
        }

        // Load the configuration.
        else {

            // Compute resourceName and configPart (if any, configPart can
            // remain null).
            int i = name.indexOf(".");
            String resourceName;
            String configPart = null;

            if (i > -1) {
                resourceName = name.substring(0, i);
                configPart = name.substring(i + 1);
            } else {
                resourceName = name;
            }

            Resource resource = loader.getResource(getConfigPrefix() + resourceName + CONFIGURATION_FILE_SUFFIX);

            if (resource.exists()) {
                try {
                    HierarchicalConfiguration config = getConfig(resource);
                    if (configPart != null) {
                        return config.configurationAt(configPart);
                    } else {
                        return config;
                    }

                } catch (Exception e) {
                    throw new ConfigurationException("Unable to load configuration for component " + name, e);
                }
            }
        }

        // Configuration was not loaded, throw exception.
        throw new ConfigurationException("Unable to load configuration for component " + name);

    }

    /**
     * @see
     * org.springframework.context.ResourceLoaderAware#setResourceLoader(org.springframework.core.io.ResourceLoader)
     */
    public void setResourceLoader(ResourceLoader loader) {
        this.loader = loader;
    }

    /**
     * Load the xmlConfiguration from the given resource.
     * 
     * @param r
     * @return
     * @throws ConfigurationException
     * @throws IOException
     */
    private XMLConfiguration getConfig(Resource r) throws ConfigurationException, IOException {
        XMLConfiguration config = new XMLConfiguration();
        config.setDelimiterParsingDisabled(true);
        
        // Don't split attributes which can have bad side-effects with matcher-conditions.
        // See JAMES-1233
        config.setAttributeSplittingDisabled(true);
        
        // Use InputStream so we are not bound to File implementations of the
        // config
        config.load(r.getInputStream());
        return config;
    }

    /**
     * Return the configuration prefix to load the configuration. In this case
     * it is classpath:, but could be also file://conf/
     * 
     * @return prefix
     */
    private String getConfigPrefix() {
        return "classpath:";
    }

}
