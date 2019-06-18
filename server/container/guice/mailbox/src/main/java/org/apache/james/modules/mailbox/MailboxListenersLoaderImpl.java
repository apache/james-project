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
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.GenericGroup;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.utils.ExtendedClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

public class MailboxListenersLoaderImpl implements Configurable, MailboxListenersLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxListenersLoaderImpl.class);

    private final MailboxListenerFactory mailboxListenerFactory;
    private final EventBus eventBus;
    private final ExtendedClassLoader classLoader;
    private final Set<MailboxListener.GroupMailboxListener> guiceDefinedListeners;

    @Inject
    MailboxListenersLoaderImpl(MailboxListenerFactory mailboxListenerFactory, EventBus eventBus,
                                  ExtendedClassLoader classLoader, Set<MailboxListener.GroupMailboxListener> guiceDefinedListeners) {
        this.mailboxListenerFactory = mailboxListenerFactory;
        this.eventBus = eventBus;
        this.classLoader = classLoader;
        this.guiceDefinedListeners = guiceDefinedListeners;
    }

    @Override
    public void configure(HierarchicalConfiguration configuration) {
        configure(ListenersConfiguration.from(configuration));
    }

    public void configure(ListenersConfiguration listenersConfiguration) {
        LOGGER.info("Loading user registered mailbox listeners");

        guiceDefinedListeners.forEach(eventBus::register);

        listenersConfiguration.getListenersConfiguration().stream()
            .map(this::createListener)
            .forEach(this::register);
    }

    @Override
    public void register(Pair<Group, MailboxListener> listener) {
        eventBus.register(listener.getRight(), listener.getLeft());
    }

    @Override
    public Pair<Group, MailboxListener> createListener(ListenerConfiguration configuration) {
        String listenerClass = configuration.getClazz();
        try {
            LOGGER.info("Loading user registered mailbox listener {}", listenerClass);
            MailboxListener mailboxListener = mailboxListenerFactory.newInstance()
                .withConfiguration(configuration.getConfiguration())
                .withExecutionMode(configuration.isAsync().map(this::getExecutionMode))
                .clazz(classLoader.locateClass(listenerClass))
                .build();


            return configuration.getGroup()
                .map(GenericGroup::new)
                .map(group -> Pair.<Group, MailboxListener>of(group, mailboxListener))
                .orElseGet(() -> withDefaultGroup(mailboxListener));
        } catch (ClassNotFoundException e) {
            LOGGER.error("Error while loading user registered global listener {}", listenerClass, e);
            throw new RuntimeException(e);
        }
    }

    private Pair<Group, MailboxListener> withDefaultGroup(MailboxListener mailboxListener) {
        Preconditions.checkArgument(mailboxListener instanceof MailboxListener.GroupMailboxListener);

        MailboxListener.GroupMailboxListener groupMailboxListener = (MailboxListener.GroupMailboxListener) mailboxListener;
        return Pair.of(groupMailboxListener.getDefaultGroup(), groupMailboxListener);
    }

    private MailboxListener.ExecutionMode getExecutionMode(boolean isAsync) {
        if (isAsync) {
            return MailboxListener.ExecutionMode.ASYNCHRONOUS;
        }
        return MailboxListener.ExecutionMode.SYNCHRONOUS;
    }
}
