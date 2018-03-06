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
import java.util.Set;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegisteredDelegatingMailboxListener implements DistributedDelegatingMailboxListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisteredDelegatingMailboxListener.class);

    private final MailboxListenerRegistry mailboxListenerRegistry;
    private final MailboxPathRegister mailboxPathRegister;
    private final Publisher publisher;
    private final EventSerializer eventSerializer;
    private final EventDelivery eventDelivery;

    public RegisteredDelegatingMailboxListener(EventSerializer eventSerializer,
                                               Publisher publisher,
                                               MessageConsumer messageConsumer,
                                               MailboxPathRegister mailboxPathRegister,
                                               EventDelivery eventDelivery) throws Exception {
        this.eventSerializer = eventSerializer;
        this.publisher = publisher;
        this.mailboxPathRegister = mailboxPathRegister;
        this.mailboxListenerRegistry = new MailboxListenerRegistry();
        this.eventDelivery = eventDelivery;
        messageConsumer.setMessageReceiver(this);
        messageConsumer.init(mailboxPathRegister.getLocalTopic());
    }

    public RegisteredDelegatingMailboxListener(EventSerializer eventSerializer,
                                               Publisher publisher,
                                               MessageConsumer messageConsumer,
                                               MailboxPathRegister mailboxPathRegister) throws Exception {
        this(eventSerializer, publisher, messageConsumer, mailboxPathRegister, new SynchronousEventDelivery());
    }

    @Override
    public ListenerType getType() {
        return ListenerType.ONCE;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return ExecutionMode.SYNCHRONOUS;
    }

    @Override
    public void addListener(MailboxPath path, MailboxListener listener, MailboxSession session) throws MailboxException {
        mailboxListenerRegistry.addListener(path, listener);
        mailboxPathRegister.register(path);
    }

    @Override
    public void addGlobalListener(MailboxListener listener, MailboxSession session) throws MailboxException {
        if (listener.getType().equals(ListenerType.EACH_NODE)) {
            throw new MailboxException("Attempt to register a global listener that need to be called on each node while using a non compatible delegating listeners");
        }
        mailboxListenerRegistry.addGlobalListener(listener);
    }

    @Override
    public void removeListener(MailboxPath mailboxPath, MailboxListener listener, MailboxSession session) throws MailboxException {
        mailboxListenerRegistry.removeListener(mailboxPath, listener);
        mailboxPathRegister.unregister(mailboxPath);
    }

    @Override
    public void removeGlobalListener(MailboxListener listener, MailboxSession session) throws MailboxException {
        mailboxListenerRegistry.removeGlobalListener(listener);
    }

    @Override
    public void event(MailboxEvent event) {
        try {
            deliverEventToOnceGlobalListeners(event);
            deliverToMailboxPathRegisteredListeners(event);
            sendToRemoteJames(event);
        } catch (Throwable t) {
            LOGGER.error("Error while delegating event {}", event.getClass().getCanonicalName(), t);
        }
    }

    public void receiveSerializedEvent(byte[] serializedEvent) {
        try {
            MailboxEvent event = eventSerializer.deSerializeEvent(serializedEvent);
            deliverToMailboxPathRegisteredListeners(event);
        } catch (Exception e) {
            LOGGER.error("Error while receiving serialized event", e);
        }
    }

    private void deliverToMailboxPathRegisteredListeners(MailboxEvent event) throws MailboxException {
        Collection<MailboxListener> listenerSnapshot = mailboxListenerRegistry.getLocalMailboxListeners(event.getMailboxPath());
        if (event instanceof MailboxDeletion && listenerSnapshot.size() > 0) {
            mailboxListenerRegistry.deleteRegistryFor(event.getMailboxPath());
            mailboxPathRegister.doCompleteUnRegister(event.getMailboxPath());
        } else if (event instanceof MailboxRenamed && listenerSnapshot.size() > 0) {
            MailboxRenamed renamed = (MailboxRenamed) event;
            mailboxListenerRegistry.handleRename(renamed.getMailboxPath(), renamed.getNewPath());
            mailboxPathRegister.doRename(renamed.getMailboxPath(), renamed.getNewPath());
        }
        for (MailboxListener listener : listenerSnapshot) {
            eventDelivery.deliver(listener, event);
        }
    }

    private void deliverEventToOnceGlobalListeners(MailboxEvent event) {
        for (MailboxListener mailboxListener : mailboxListenerRegistry.getGlobalListeners()) {
            if (mailboxListener.getType() == ListenerType.ONCE) {
                eventDelivery.deliver(mailboxListener, event);
            }
        }
    }

    private void sendToRemoteJames(MailboxEvent event) {
        Set<Topic> topics = mailboxPathRegister.getTopics(event.getMailboxPath());
        topics.remove(mailboxPathRegister.getLocalTopic());
        if (topics.size() > 0) {
            sendEventToRemotesJamesByTopic(event, topics);
        }
    }

    private void sendEventToRemotesJamesByTopic(MailboxEvent event, Set<Topic> topics) {
        byte[] serializedEvent;
        try {
            serializedEvent = eventSerializer.serializeEvent(event);
        } catch (Exception e) {
            LOGGER.error("Unable to serialize {}", event.getClass().getCanonicalName(), e);
            return;
        }
        for (Topic topic : topics) {
            try {
                publisher.publish(topic, serializedEvent);
            } catch (Throwable t) {
                LOGGER.error("Unable to send serialized event to topic {}", topic);
            }
        }
    }

}
