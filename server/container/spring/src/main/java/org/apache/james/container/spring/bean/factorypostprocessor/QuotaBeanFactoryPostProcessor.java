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

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.container.spring.lifecycle.ConfigurationProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

public class QuotaBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    private static final String IN_MEMORY_IMPLEMENTATION = "inmemory";
    private static final String CASSANDRA_IMPLEMENTATION = "cassandra";
    private static final String FAKE_IMPLEMENTATION = "fake";
    private static final String MAX_QUOTA_MANAGER = "maxQuotaManager";
    private static final String JPA_IMPLEMENTATION = "jpa";
    private static final String DEFAULT_IMPLEMENTATION = "default";

    private static final String QUOTA_MANAGER_BEAN = "quotaManager";
    private static final String QUOTA_UPDATER_BEAN = "quotaUpdater";
    private static final String QUOTA_ROOT_RESOLVER_BEAN = "quotaRootResolver";
    private static final String CURRENT_QUOTA_MANAGER_BEAN = "currentQuotaManager";
    private static final String UPDATES_BEAN = "updates";

    private static final String PROVIDER = "provider";
    private static final String EVENT = "event";

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        ConfigurationProvider confProvider = beanFactory.getBean(ConfigurationProvider.class);
        try {
            HierarchicalConfiguration<ImmutableNode> config = confProvider.getConfiguration("quota");

            String quotaRootResolver = config.configurationAt(QUOTA_ROOT_RESOLVER_BEAN).getString(PROVIDER, DEFAULT_IMPLEMENTATION);
            String currentQuotaManager = config.configurationAt(CURRENT_QUOTA_MANAGER_BEAN).getString(PROVIDER, "none");
            String maxQuotaManager = config.configurationAt(MAX_QUOTA_MANAGER).getString(PROVIDER, FAKE_IMPLEMENTATION);
            String quotaManager = config.configurationAt(QUOTA_MANAGER_BEAN).getString(PROVIDER, FAKE_IMPLEMENTATION);
            String quotaUpdater = config.configurationAt(UPDATES_BEAN).getString(PROVIDER, FAKE_IMPLEMENTATION);

            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

            registerAliasForQuotaRootResolver(quotaRootResolver, registry);
            registerAliasForCurrentQuotaManager(currentQuotaManager, registry);
            registerAliasForMaxQuotaManager(maxQuotaManager, registry);
            registerAliasForQuotaManager(quotaManager, registry);
            registerAliasForQuotaUpdater(quotaUpdater, registry);
        } catch (ConfigurationException e) {
            throw new FatalBeanException("Unable to configure Quota system", e);
        }
    }

    private void registerAliasForQuotaUpdater(String quotaUpdater, BeanDefinitionRegistry registry) {
        if (quotaUpdater.equalsIgnoreCase(EVENT)) {
            registry.registerAlias("eventQuotaUpdater", QUOTA_UPDATER_BEAN);
        } else if (quotaUpdater.equalsIgnoreCase(FAKE_IMPLEMENTATION)) {
            registry.registerAlias("noQuotaUpdater", QUOTA_UPDATER_BEAN);
        } else {
            throw new FatalBeanException("Unreadable value for Quota Updater : " + quotaUpdater);
        }
    }

    private void registerAliasForQuotaManager(String quotaManager, BeanDefinitionRegistry registry) {
        if (quotaManager.equalsIgnoreCase(FAKE_IMPLEMENTATION)) {
            registry.registerAlias("noQuotaManager", QUOTA_MANAGER_BEAN);
        } else if (quotaManager.equalsIgnoreCase("store")) {
            registry.registerAlias("storeQuotaManager", QUOTA_MANAGER_BEAN);
        } else {
            throw new FatalBeanException("Unreadable value for Quota Manager : " + quotaManager);
        }
    }

    private void registerAliasForMaxQuotaManager(String maxQuotaManager, BeanDefinitionRegistry registry) {
        if (maxQuotaManager.equalsIgnoreCase(FAKE_IMPLEMENTATION)) {
            registry.registerAlias("noMaxQuotaManager", MAX_QUOTA_MANAGER);
        } else if (maxQuotaManager.equalsIgnoreCase(IN_MEMORY_IMPLEMENTATION)) {
            registry.registerAlias("inMemoryMaxQuotaManager", MAX_QUOTA_MANAGER);
        } else if (maxQuotaManager.equalsIgnoreCase(CASSANDRA_IMPLEMENTATION)) {
            registry.registerAlias("cassandraMaxQuotaManager", MAX_QUOTA_MANAGER);
        } else if (maxQuotaManager.equalsIgnoreCase(JPA_IMPLEMENTATION)) {
            registry.registerAlias("jpaMaxQuotaManager", MAX_QUOTA_MANAGER);
        } else {
            throw new FatalBeanException("Unreadable value for Max Quota Manager : " + maxQuotaManager);
        }
    }

    private void registerAliasForCurrentQuotaManager(String currentQuotaManager, BeanDefinitionRegistry registry) {
        if (currentQuotaManager.equalsIgnoreCase(IN_MEMORY_IMPLEMENTATION)) {
            registry.registerAlias("inMemoryCurrentQuotaManager", CURRENT_QUOTA_MANAGER_BEAN);
        } else if (currentQuotaManager.equalsIgnoreCase(CASSANDRA_IMPLEMENTATION)) {
            registry.registerAlias("cassandraCurrentQuotaManager", CURRENT_QUOTA_MANAGER_BEAN);
        }  else if (currentQuotaManager.equalsIgnoreCase(JPA_IMPLEMENTATION)) {
            registry.registerAlias("jpaCurrentQuotaManager", CURRENT_QUOTA_MANAGER_BEAN);
        } else if (! currentQuotaManager.equalsIgnoreCase("none")) {
            throw new FatalBeanException("Unreadable value for Current Quota Manager : " + currentQuotaManager);
        }
    }

    private void registerAliasForQuotaRootResolver(String quotaRootResolver, BeanDefinitionRegistry registry) {
        if (quotaRootResolver.equals(DEFAULT_IMPLEMENTATION)) {
            registry.registerAlias("defaultQuotaRootResolver", QUOTA_ROOT_RESOLVER_BEAN);
        } else {
            throw new FatalBeanException("Unreadable value for QUOTA ROOT resolver : " + quotaRootResolver);
        }
    }
}
