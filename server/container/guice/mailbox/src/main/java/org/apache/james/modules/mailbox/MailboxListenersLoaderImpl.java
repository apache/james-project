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
package org.apache.james.modules.mailbox;

import java.util.Set;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.event.MailboxListenerRegistry;
import org.apache.james.utils.ExtendedClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class MailboxListenersLoaderImpl implements Configurable, MailboxListenersLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxListenersLoaderImpl.class);

    private final MailboxListenerFactory mailboxListenerFactory;
    private final MailboxListenerRegistry registry;
    private final ExtendedClassLoader classLoader;
    private final Set<MailboxListener> guiceDefinedListeners;

    @Inject
    public MailboxListenersLoaderImpl(MailboxListenerFactory mailboxListenerFactory, MailboxListenerRegistry registry,
                                  ExtendedClassLoader classLoader, Set<MailboxListener> guiceDefinedListeners) {
        this.mailboxListenerFactory = mailboxListenerFactory;
        this.registry = registry;
        this.classLoader = classLoader;
        this.guiceDefinedListeners = guiceDefinedListeners;
    }

    @Override
    public void configure(HierarchicalConfiguration configuration) {
        LOGGER.info("Loading user registered mailbox listeners");

        ListenersConfiguration listenersConfiguration = ListenersConfiguration.from(configuration);

        guiceDefinedListeners.forEach(this::register);

        listenersConfiguration.getListenersConfiguration().stream()
            .map(this::createListener)
            .forEach(this::register);
    }

    @Override
    public void register(MailboxListener listener) {
        try {
            registry.addGlobalListener(listener);
        } catch (MailboxException e) {
            LOGGER.error("Error while registering global listener {}", listener, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public MailboxListener createListener(ListenerConfiguration configuration) {
        String listenerClass = configuration.getClazz();
        try {
            LOGGER.info("Loading user registered mailbox listener {}", listenerClass);
            return mailboxListenerFactory.newInstance()
                .withConfiguration(configuration.getConfiguration())
                .withExecutionMode(configuration.isAsync().map(this::getExecutionMode))
                .clazz(classLoader.locateClass(listenerClass))
                .build();
        } catch (ClassNotFoundException e) {
            LOGGER.error("Error while loading user registered global listener {}", listenerClass, e);
            throw new RuntimeException(e);
        }
    }

    private MailboxListener.ExecutionMode getExecutionMode(boolean isAsync) {
        if (isAsync) {
            return MailboxListener.ExecutionMode.ASYNCHRONOUS;
        }
        return MailboxListener.ExecutionMode.SYNCHRONOUS;
    }
}
