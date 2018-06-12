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

package org.apache.james.mailbox.store.event.distributed;

import java.util.Collection;

import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.event.EventDelivery;
import org.apache.james.mailbox.store.event.EventSerializer;
import org.apache.james.mailbox.store.event.MailboxListenerRegistry;
import org.apache.james.mailbox.store.event.SynchronousEventDelivery;
import org.apache.james.mailbox.store.publisher.MessageConsumer;
import org.apache.james.mailbox.store.publisher.Publisher;
import org.apache.james.mailbox.store.publisher.Topic;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class BroadcastDelegatingMailboxListener implements DistributedDelegatingMailboxListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(BroadcastDelegatingMailboxListener.class);

    private final MailboxListenerRegistry mailboxListenerRegistry;
    private final Publisher publisher;
    private final EventSerializer eventSerializer;
    private final Topic globalTopic;
    private final EventDelivery eventDelivery;

    public BroadcastDelegatingMailboxListener(Publisher publisher,
                                              MessageConsumer messageConsumer,
                                              EventSerializer eventSerializer,
                                              EventDelivery eventDelivery,
                                              String globalTopic) throws Exception {
        this.mailboxListenerRegistry = new MailboxListenerRegistry();
        this.publisher = publisher;
        this.eventSerializer = eventSerializer;
        this.globalTopic = new Topic(globalTopic);
        this.eventDelivery = eventDelivery;
        messageConsumer.setMessageReceiver(this);
        messageConsumer.init(this.globalTopic);
    }

    @VisibleForTesting
    public BroadcastDelegatingMailboxListener(Publisher publisher,
                                              MessageConsumer messageConsumer,
                                              EventSerializer eventSerializer,
                                              String globalTopic) throws Exception {
        this(publisher, messageConsumer, eventSerializer, new SynchronousEventDelivery(new NoopMetricFactory()), globalTopic);
    }

    @Override
    public ListenerType getType() {
        return ListenerType.ONCE;
    }

    @Override
    public void addListener(MailboxPath mailboxPath, MailboxListener listener, MailboxSession session) throws MailboxException {
        mailboxListenerRegistry.addListener(mailboxPath, listener);
    }

    @Override
    public void removeListener(MailboxPath mailboxPath, MailboxListener listener, MailboxSession session) throws MailboxException {
        mailboxListenerRegistry.removeListener(mailboxPath, listener);
    }

    @Override
    public void addGlobalListener(MailboxListener listener, MailboxSession session) throws MailboxException {
        mailboxListenerRegistry.addGlobalListener(listener);
    }

    @Override
    public void removeGlobalListener(MailboxListener listener, MailboxSession session) throws MailboxException {
        mailboxListenerRegistry.removeGlobalListener(listener);
    }

    @Override
    public void event(Event event) {
        deliverEventToGlobalListeners(event, ListenerType.ONCE);
        if (event instanceof MailboxEvent) {
            MailboxEvent mailboxEvent = (MailboxEvent) event;
            publishMailboxEvent(mailboxEvent);
        }
    }

    private void publishMailboxEvent(MailboxEvent event) {
        try {
            publisher.publish(globalTopic, eventSerializer.serializeEvent(event));
        } catch (Throwable t) {
            LOGGER.error("Error while sending event to publisher", t);
        }
    }

    @Override
    public void receiveSerializedEvent(byte[] serializedEvent) {
        try {
            MailboxEvent event = eventSerializer.deSerializeEvent(serializedEvent);
            deliverToMailboxPathRegisteredListeners(event);
            deliverEventToGlobalListeners(event, ListenerType.EACH_NODE);
        } catch (Exception e) {
            LOGGER.error("Error while receiving serialized event", e);
        }
    }

    private void deliverToMailboxPathRegisteredListeners(MailboxEvent event) {
        Collection<MailboxListener> listenerSnapshot = mailboxListenerRegistry.getLocalMailboxListeners(event.getMailboxPath());
        if (event instanceof MailboxDeletion) {
            mailboxListenerRegistry.deleteRegistryFor(event.getMailboxPath());
        } else if (event instanceof MailboxRenamed) {
            MailboxRenamed renamed = (MailboxRenamed) event;
            mailboxListenerRegistry.handleRename(renamed.getMailboxPath(), renamed.getNewPath());
        }
        for (MailboxListener listener : listenerSnapshot) {
            eventDelivery.deliver(listener, event);
        }
    }

    private void deliverEventToGlobalListeners(Event event, ListenerType type) {
        for (MailboxListener mailboxListener : mailboxListenerRegistry.getGlobalListeners()) {
            if (mailboxListener.getType() == type) {
                eventDelivery.deliver(mailboxListener, event);
            }
        }
    }

}
