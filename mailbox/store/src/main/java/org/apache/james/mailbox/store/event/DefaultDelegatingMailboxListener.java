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

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;

/**
 * Receive a {@link org.apache.james.mailbox.MailboxListener.Event} and delegate it to an other
 * {@link MailboxListener} depending on the registered name
 *
 * This is a mono instance Thread safe implementation for DelegatingMailboxListener
 */
public class DefaultDelegatingMailboxListener implements DelegatingMailboxListener {

    private MailboxListenerRegistry registry;

    @Override
    public ListenerType getType() {
        return ListenerType.EACH_NODE;
    }

    public DefaultDelegatingMailboxListener() {
        this.registry = new MailboxListenerRegistry();
    }

    @Override
    public void addListener(MailboxPath path, MailboxListener listener, MailboxSession session) throws MailboxException {
        if (listener.getType() != ListenerType.MAILBOX) {
            throw new MailboxException(listener.getClass().getCanonicalName() + " registred on specific MAILBOX operation while its listener type was " + listener.getType().getValue());
        }
        registry.addListener(path, listener);
    }

    @Override
    public void addGlobalListener(MailboxListener listener, MailboxSession session) throws MailboxException {
        if (listener.getType() != ListenerType.EACH_NODE && listener.getType() != ListenerType.ONCE) {
            throw new MailboxException(listener.getClass().getCanonicalName() + " registered on global event dispatching while its listener type was " + listener.getType().getValue());
        }
        registry.addGlobalListener(listener);
    }

    @Override
    public void removeListener(MailboxPath mailboxPath, MailboxListener listener, MailboxSession session) throws MailboxException {
        registry.removeListener(mailboxPath, listener);
    }

    @Override
    public void removeGlobalListener(MailboxListener listener, MailboxSession session) throws MailboxException {
        registry.removeGlobalListener(listener);
    }

    @Override
    public void event(Event event) {
        Collection<MailboxListener> listenerSnapshot = registry.getLocalMailboxListeners(event.getMailboxPath());
        if (event instanceof MailboxDeletion) {
            registry.deleteRegistryFor(event.getMailboxPath());
        } else if (event instanceof MailboxRenamed) {
            MailboxRenamed renamed = (MailboxRenamed) event;
            registry.handleRename(renamed.getMailboxPath(), renamed.getNewPath());
        }
        deliverEventToMailboxListeners(event, listenerSnapshot);
        deliverEventToGlobalListeners(event);
    }

    protected void deliverEventToMailboxListeners(Event event, Collection<MailboxListener> listenerSnapshot) {
        for (MailboxListener listener : listenerSnapshot) {
            deliverEvent(event, listener);
        }
    }

    protected void deliverEventToGlobalListeners(Event event) {
        for (MailboxListener mailboxListener : registry.getGlobalListeners()) {
            deliverEvent(event, mailboxListener);
        }
    }

    private void deliverEvent(Event event, MailboxListener listener) {
        listener.event(event);
    }

}
