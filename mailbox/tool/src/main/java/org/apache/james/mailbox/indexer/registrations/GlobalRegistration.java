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

package org.apache.james.mailbox.indexer.registrations;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.model.MailboxPath;

public class GlobalRegistration implements MailboxListener {

    private final ConcurrentHashMap<MailboxPath, Boolean> isPathDeleted;
    private final ConcurrentHashMap<MailboxPath, MailboxPath> nameCorrespondence;

    public GlobalRegistration() {
        this.isPathDeleted = new ConcurrentHashMap<>();
        this.nameCorrespondence = new ConcurrentHashMap<>();
    }

    public Optional<MailboxPath> getPathToIndex(MailboxPath mailboxPath) {
        if (isPathDeleted.get(mailboxPath) != null) {
            return Optional.empty();
        }
        return Optional.of(
            Optional.ofNullable(nameCorrespondence.get(mailboxPath)).orElse(mailboxPath));
    }

    @Override
    public ListenerType getType() {
        return ListenerType.EACH_NODE;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return ExecutionMode.SYNCHRONOUS;
    }

    @Override
    public void event(Event event) {
        if (event instanceof MailboxDeletion) {
            MailboxDeletion mailboxDeletion = (MailboxDeletion) event;
            isPathDeleted.put(mailboxDeletion.getMailboxPath(), true);
        } else if (event instanceof MailboxRenamed) {
            MailboxRenamed mailboxRenamed = (MailboxRenamed) event;
            nameCorrespondence.put(mailboxRenamed.getMailboxPath(), ((MailboxRenamed) event).getNewPath());
        }
    }
}
