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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.container.spring.lifecycle.ConfigurationProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * Read indexer.xml file and register the right bean alias in the
 * {@link BeanDefinitionRegistry} depending on the configured provider. As
 * default jpa is used!
 * 
 * It will register it with the alias mailboxmanager
 */
public class IndexerConfigurationBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        ConfigurationProvider confProvider = beanFactory.getBean(ConfigurationProvider.class);
        try {
            HierarchicalConfiguration config = confProvider.getConfiguration("indexer");
            String provider = config.getString("provider", "lazyIndex");

            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
            String indexer = null;
            String reIndexer = null;
            if (provider.equalsIgnoreCase("lazyIndex")) {
                indexer = "lazyIndex";
                reIndexer = "fake-reindexer";
            } else if (provider.equalsIgnoreCase("elasticsearch")) {
                indexer = "elasticsearch-listener";
                reIndexer = "reindexer-impl";
            } else if (provider.equalsIgnoreCase("luceneIndex")) {
                indexer = "luceneIndex";
                reIndexer = "fake-reindexer";
            }

            if (indexer == null) {
                throw new ConfigurationException("Indexer provider " + provider + " not supported!");
            }
            registry.registerAlias(indexer, "indexer");
            registry.registerAlias(reIndexer, "reindexer");

        } catch (ConfigurationException e) {
            throw new FatalBeanException("Unable to config the indexer", e);
        }

    }

}
