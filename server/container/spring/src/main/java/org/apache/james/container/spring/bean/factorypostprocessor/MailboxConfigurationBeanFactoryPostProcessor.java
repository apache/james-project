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

import com.google.common.collect.ImmutableSet;

/**
 * Read mailbox.xml file and register the right bean alias in the
 * {@link BeanDefinitionRegistry} depending on the configured provider. As
 * default jpa is used!
 * 
 * It will register it with the alias mailboxmanager
 */
public class MailboxConfigurationBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    private static final String JPA_MAILBOXMANAGER = "jpa-mailboxmanager";
    private static final String MEMORY_MAILBOX_MANAGER = "memory-mailboxManager";
    private static final ImmutableSet<String> MAILBOX_MANAGER_IDS = ImmutableSet.of(JPA_MAILBOXMANAGER, MEMORY_MAILBOX_MANAGER);

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        ConfigurationProvider confProvider = beanFactory.getBean(ConfigurationProvider.class);
        try {
            HierarchicalConfiguration<ImmutableNode> config = confProvider.getConfiguration("mailbox");
            String provider = config.getString("provider", "jpa");

            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
            String mailbox = null;
            String subscription = null;
            String messageMapperFactory = null;
            String mailboxIdDeserializer = null;
            String mailboxIdFactory = null;
            if (provider.equalsIgnoreCase("jpa")) {
                mailbox = JPA_MAILBOXMANAGER;
                subscription = "jpa-subscriptionManager";
                messageMapperFactory = "jpa-sessionMapperFactory";
                mailboxIdDeserializer = "jpa-mailbox-id-deserializer";
                mailboxIdFactory = "jpa-mailboxIdFactory";
            } else if (provider.equalsIgnoreCase("memory")) {
                mailbox = MEMORY_MAILBOX_MANAGER;
                subscription = "memory-subscriptionManager";
                messageMapperFactory = "memory-sessionMapperFactory";
                mailboxIdDeserializer = "memory-mailbox-id-deserializer";
                mailboxIdFactory = "memory-mailboxIdFactory";
            }

            if (mailbox == null) {
                throw new ConfigurationException("Mailboxmanager provider " + provider + " not supported!");
            }
            registry.registerAlias(mailbox, "mailboxmanager");
            registry.registerAlias(subscription, "subscriptionManager");
            registry.registerAlias(messageMapperFactory, "messageMapperFactory");
            registry.registerAlias(mailboxIdDeserializer, "mailbox-id-deserializer");
            registry.registerAlias(mailboxIdFactory, "mailboxIdFactory");

            removeMailboxManagersExceptRightSelectedOne(registry, mailbox);

        } catch (ConfigurationException e) {
            throw new FatalBeanException("Unable to config the mailboxmanager", e);
        }

    }

    private void removeMailboxManagersExceptRightSelectedOne(BeanDefinitionRegistry registry, String selectedMailboxManager) {
        for (String mailboxManagerId : MAILBOX_MANAGER_IDS) {
            if (registeredAndNotSelected(registry, selectedMailboxManager, mailboxManagerId)) {
                registry.removeBeanDefinition(mailboxManagerId);
            }
        }
    }

    private boolean registeredAndNotSelected(BeanDefinitionRegistry registry, String selectedMailboxManager, String otherMailboxManager) {
        return !otherMailboxManager.equals(selectedMailboxManager) && registry.containsBeanDefinition(otherMailboxManager);
    }

}
