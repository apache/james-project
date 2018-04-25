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

import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.event.MailboxListenerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.google.inject.Injector;

public class GlobalMailboxListeners implements Configurable {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalMailboxListeners.class);

    private final Injector injector;
    private final MailboxListenerRegistry registry;

    @Inject
    public GlobalMailboxListeners(Injector injector, MailboxListenerRegistry registry) {
        this.injector = injector;
        this.registry = registry;
    }

    @Override
    public void configure(HierarchicalConfiguration configuration) throws ConfigurationException {
        LOGGER.info("Loading mailbox listeners");
        List<HierarchicalConfiguration> listenersConfiguration = configuration.configurationsAt("listener");
        listenersConfiguration.stream()
            .forEach(listenerConfiguration -> configureListener(listenerConfiguration));
    }

    @VisibleForTesting void configureListener(HierarchicalConfiguration configuration) {
        String listenerClass = configuration.getString("class");
        Preconditions.checkState(!Strings.isNullOrEmpty(listenerClass), "class name is mandatory");
        try {
            LOGGER.info("Loading mailbox listener {}", listenerClass);
            registry.addGlobalListener(injector.getInstance(loadMailboxListener(listenerClass)));
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | MailboxException e) {
            LOGGER.error("Error while loading global listener {}", listenerClass, e);
            Throwables.propagate(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Class<MailboxListener> loadMailboxListener(String listenerClass) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        Class<?> clazz = ClassLoader.getSystemClassLoader().loadClass(listenerClass);
        return (Class<MailboxListener>) clazz;
    }
}
