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
 * Read mailbox.xml file and register the right bean alias in the
 * {@link BeanDefinitionRegistry} depending on the configured provider. As
 * default jpa is used!
 * 
 * It will register it with the alias mailboxmanager
 */
public class MailboxConfigurationBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    /**
     * @see org.springframework.beans.factory.config.BeanFactoryPostProcessor#postProcessBeanFactory
     * (org.springframework.beans.factory.config.ConfigurableListableBeanFactory)
     */
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        ConfigurationProvider confProvider = beanFactory.getBean(ConfigurationProvider.class);
        try {
            HierarchicalConfiguration config = confProvider.getConfiguration("mailbox");
            String provider = config.getString("provider", "jpa");

            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
            String mailbox = null;
            String subscription = null;
            String messageMapperFactory = null;
            String mailboxIdDeserializer = null;
            if (provider.equalsIgnoreCase("jpa")) {
                mailbox = "jpa-mailboxmanager";
                subscription = "jpa-subscriptionManager";
                messageMapperFactory = "jpa-sessionMapperFactory";
                mailboxIdDeserializer = "jpa-mailbox-id-deserializer";
            } else if (provider.equalsIgnoreCase("memory")) {
                mailbox = "memory-mailboxmanager";
                subscription = "memory-subscriptionManager";
                messageMapperFactory = "memory-sessionMapperFactory";
                mailboxIdDeserializer = "memory-mailbox-id-deserializer";
            } else if (provider.equalsIgnoreCase("jcr")) {
                mailbox = "jcr-mailboxmanager";
                subscription = "jcr-subscriptionManager";
                messageMapperFactory = "jcr-sessionMapperFactory";
                mailboxIdDeserializer = "jcr-mailbox-id-deserializer";
            } else if (provider.equalsIgnoreCase("maildir")) {
                mailbox = "maildir-mailboxmanager";
                subscription = "maildir-subscriptionManager";
                messageMapperFactory = "maildir-sessionMapperFactory";
                mailboxIdDeserializer = "maildir-mailbox-id-deserializer";
            } else if (provider.equalsIgnoreCase("hbase")) {
                mailbox = "hbase-mailboxmanager";
                subscription = "hbase-subscriptionManager";
                messageMapperFactory = "hbase-sessionMapperFactory";
                mailboxIdDeserializer = "hbase-mailbox-id-deserializer";
            } else if (provider.equalsIgnoreCase("cassandra")) {
                mailbox = "cassandra-mailboxmanager";
                subscription = "cassandra-subscriptionManager";
                messageMapperFactory = "cassandra-sessionMapperFactory";
                mailboxIdDeserializer = "cassandra-mailbox-id-deserializer";
            }

            if (mailbox == null)
                throw new ConfigurationException("Mailboxmanager provider " + provider + " not supported!");
            registry.registerAlias(mailbox, "mailboxmanager");
            registry.registerAlias(subscription, "subscriptionManager");
            registry.registerAlias(messageMapperFactory, "messageMapperFactory");
            registry.registerAlias(mailboxIdDeserializer, "mailbox-id-deserializer");

        } catch (ConfigurationException e) {
            throw new FatalBeanException("Unable to config the mailboxmanager", e);
        }

    }

}
