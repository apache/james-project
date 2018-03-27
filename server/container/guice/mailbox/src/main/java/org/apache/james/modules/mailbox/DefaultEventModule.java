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
import java.util.Set;

import javax.inject.Named;

import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.event.DelegatingMailboxListener;
import org.apache.james.mailbox.store.event.EventDelivery;
import org.apache.james.mailbox.store.event.SynchronousEventDelivery;
import org.apache.james.modules.Names;
import org.apache.james.utils.ConfigurationPerformer;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class DefaultEventModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DefaultDelegatingMailboxListener.class).in(Scopes.SINGLETON);
        bind(DelegatingMailboxListener.class).to(DefaultDelegatingMailboxListener.class);

        bind(SynchronousEventDelivery.class).in(Scopes.SINGLETON);
        bind(EventDelivery.class).to(SynchronousEventDelivery.class);

        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(ListenerRegistrationPerformer.class);
        Multibinder.newSetBinder(binder(), MailboxListener.class);
    }

    @Singleton
    public static class ListenerRegistrationPerformer implements ConfigurationPerformer {
        private final MailboxManager mailboxManager;
        private final Set<MailboxListener> listeners;

        @Inject
        public ListenerRegistrationPerformer(@Named(Names.MAILBOXMANAGER_NAME) MailboxManager mailboxManager,
                                             Set<MailboxListener> listeners) {
            this.mailboxManager = mailboxManager;
            this.listeners = listeners;
        }

        @Override
        public void initModule() {
            try {
                MailboxSession systemSession = mailboxManager.createSystemSession("storeMailboxManager");
                listeners.forEach(Throwing.consumer(listener ->
                    mailboxManager.addGlobalListener(listener, systemSession)));
            } catch (MailboxException e) {
                Throwables.propagate(e);
            }
        }

        @Override
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of();
        }
    }
}
