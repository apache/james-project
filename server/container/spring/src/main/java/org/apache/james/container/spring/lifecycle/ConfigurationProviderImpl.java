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

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.commons.configuration2.tree.ImmutableNode;
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
    private final Map<String, HierarchicalConfiguration<ImmutableNode>> configurations = new HashMap<>();

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

    @Override
    public void registerConfiguration(String beanName, HierarchicalConfiguration<ImmutableNode> conf) {
        configurations.put(beanName, conf);
    }

    /**
     * Responsible to register additional configurations for the injected
     * configurationMappings.
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        if (configurationMappings != null) {
            for (String key : configurationMappings.keySet()) {
                String value = configurationMappings.get(key);
                registerConfiguration(key, getConfiguration(value));
            }
        }
    }

    @Override
    public HierarchicalConfiguration<ImmutableNode> getConfiguration(String name) throws ConfigurationException {

        HierarchicalConfiguration<ImmutableNode> conf = configurations.get(name);

        // Simply return the configuration if it is already loaded.
        if (conf != null) {
            return conf;
        } else {
            // Load the configuration.

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
                    HierarchicalConfiguration<ImmutableNode> config = getConfig(resource);
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

    @Override
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
        FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<>(XMLConfiguration.class)
            .configure(new Parameters()
                .xml()
                .setListDelimiterHandler(new DisabledListDelimiterHandler()));
        XMLConfiguration xmlConfiguration = builder.getConfiguration();
        FileHandler fileHandler = new FileHandler(xmlConfiguration);
        fileHandler.load(r.getInputStream());

        return xmlConfiguration;
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
