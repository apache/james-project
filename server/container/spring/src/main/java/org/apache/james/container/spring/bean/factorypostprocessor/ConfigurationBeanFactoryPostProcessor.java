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
package org.apache.james.container.spring.bean.factorypostprocessor;

import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.container.spring.lifecycle.ConfigurationProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * {@link BeanFactoryPostProcessor} which lookup the configuration file for the
 * configured beans and register the right class with the given beanname for it.
 * The class is lookup-ed via the class="" tag in the configuration file. The
 * lookup of the configuration file is done via the
 * {@link ConfigurationProvider#getConfiguration(String)} method. Which take the
 * beanname as argument
 * 
 * It also support to register aliases for the beans. The value of the map entry
 * is used as a comma-seperated list of aliases. If you don't need to register
 * an alias just us an empty value.
 */
public class ConfigurationBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    private Map<String, String> beans;

    public void setBeans(Map<String, String> beans) {
        this.beans = beans;
    }

    /**
     * Parse the configuration file and depending on it register the beans
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

        ConfigurationProvider confProvider = beanFactory.getBean(ConfigurationProvider.class);

        // loop over the beans
        for (String name : beans.keySet()) {

            try {

                HierarchicalConfiguration config = confProvider.getConfiguration(name);

                // Get the configuration for the class
                String repClass = config.getString("[@class]");

                // Create the definition and register it
                BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
                BeanDefinition def = BeanDefinitionBuilder.genericBeanDefinition(repClass).getBeanDefinition();
                registry.registerBeanDefinition(name, def);

                String aliases = beans.get(name);
                String[] aliasArray = aliases.split(",");

                // check if we need to register some aliases for this bean
                if (aliasArray != null) {
                    for (String anAliasArray : aliasArray) {
                        String alias = anAliasArray.trim();
                        if (alias.length() > 0) {
                            registry.registerAlias(name, anAliasArray);
                        }
                    }
                }

            } catch (ConfigurationException e) {
                throw new FatalBeanException("Unable to parse configuration for bean " + name, e);
            }
        }

    }

}
