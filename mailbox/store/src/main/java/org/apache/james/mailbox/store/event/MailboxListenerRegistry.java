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

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

public class MailboxListenerRegistry {

    private final Multimap<MailboxPath, MailboxListener> listeners;
    private final ConcurrentLinkedQueue<MailboxListener> globalListeners;

    public MailboxListenerRegistry() {
        this.globalListeners = new ConcurrentLinkedQueue<>();
        this.listeners = Multimaps.synchronizedMultimap(HashMultimap.<MailboxPath, MailboxListener>create());
    }

    public void addListener(MailboxPath path, MailboxListener listener) throws MailboxException {
        listeners.put(path, listener);
    }

    public void addGlobalListener(MailboxListener listener) throws MailboxException {
        globalListeners.add(listener);
    }

    public void removeListener(MailboxPath mailboxPath, MailboxListener listener) throws MailboxException {
        listeners.remove(mailboxPath, listener);
    }

    public void removeGlobalListener(MailboxListener listener) throws MailboxException {
        globalListeners.remove(listener);
    }

    public List<MailboxListener> getLocalMailboxListeners(MailboxPath path) {
        return ImmutableList.copyOf(listeners.get(path));
    }

    public List<MailboxListener> getGlobalListeners() {
        return ImmutableList.copyOf(globalListeners);
    }

    public void deleteRegistryFor(MailboxPath path) {
        listeners.removeAll(path);
    }

    public void handleRename(MailboxPath oldName, MailboxPath newName) {
        listeners.putAll(newName, listeners.removeAll(oldName));
    }

}
