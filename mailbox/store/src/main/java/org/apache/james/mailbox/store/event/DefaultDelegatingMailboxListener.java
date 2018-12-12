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

package org.apache.james.mailbox.store.event;

import java.util.Collection;

import javax.inject.Inject;

import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.delivery.EventDelivery;
import org.apache.james.mailbox.events.delivery.EventDeliveryImpl;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.metrics.api.NoopMetricFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * Receive a {@link org.apache.james.mailbox.MailboxListener.MailboxEvent} and delegate it to an other
 * {@link MailboxListener} depending on the registered mailboxId
 *
 * This is a mono instance Thread safe implementation for DelegatingMailboxListener
 */
public class DefaultDelegatingMailboxListener implements DelegatingMailboxListener {

    private final MailboxListenerRegistry registry;
    private final EventDelivery eventDelivery;

    @Override
    public ListenerType getType() {
        return ListenerType.EACH_NODE;
    }

    @VisibleForTesting
    public DefaultDelegatingMailboxListener() {
        this(new EventDeliveryImpl(new NoopMetricFactory()),
            new MailboxListenerRegistry());
    }

    @Inject
    public DefaultDelegatingMailboxListener(EventDelivery eventDelivery, MailboxListenerRegistry registry) {
        this.registry = registry;
        this.eventDelivery = eventDelivery;
    }

    @Override
    public void addListener(MailboxId mailboxId, MailboxListener listener, MailboxSession session) throws MailboxException {
        if (listener.getType() != ListenerType.MAILBOX) {
            throw new MailboxException(listener.getClass().getCanonicalName() + " registred on specific MAILBOX operation while its listener type was " + listener.getType());
        }
        registry.addListener(mailboxId, listener);
    }

    @Override
    public void addGlobalListener(MailboxListener listener, MailboxSession session) throws MailboxException {
        if (listener.getType() != ListenerType.EACH_NODE && listener.getType() != ListenerType.ONCE) {
            throw new MailboxException(listener.getClass().getCanonicalName() + " registered on global event dispatching while its listener type was " + listener.getType());
        }
        registry.addGlobalListener(listener);
    }

    @Override
    public void removeListener(MailboxId mailboxId, MailboxListener listener, MailboxSession session) {
        registry.removeListener(mailboxId, listener);
    }

    @Override
    public void removeGlobalListener(MailboxListener listener, MailboxSession session) {
        registry.removeGlobalListener(listener);
    }

    @Override
    public void event(Event event) {
        ImmutableList<MailboxListener> listeners = ImmutableList.<MailboxListener>builder()
            .addAll(registry.getGlobalListeners())
            .addAll(registeredMailboxListeners(event))
            .build();

        eventDelivery.deliver(listeners, event)
            .synchronousListenerFuture()
            .join();

        if (event instanceof MailboxDeletion) {
            MailboxDeletion deletion = (MailboxDeletion) event;
            registry.deleteRegistryFor(deletion.getMailboxId());
        }
    }

    private Collection<MailboxListener> registeredMailboxListeners(Event event) {
        if (event instanceof MailboxEvent) {
            MailboxEvent mailboxEvent = (MailboxEvent) event;

            return registry.getLocalMailboxListeners(mailboxEvent.getMailboxId());
        }
        return ImmutableList.of();
    }

    public MailboxListenerRegistry getRegistry() {
        return registry;
    }
}
