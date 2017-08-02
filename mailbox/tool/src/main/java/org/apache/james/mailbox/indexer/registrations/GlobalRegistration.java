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

import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.model.MailboxPath;

import com.google.common.base.Optional;

public class GlobalRegistration implements MailboxListener {

    private final ConcurrentHashMap<MailboxPath, Boolean> isPathDeleted;
    private final ConcurrentHashMap<MailboxPath, MailboxPath> nameCorrespondence;

    public GlobalRegistration() {
        this.isPathDeleted = new ConcurrentHashMap<>();
        this.nameCorrespondence = new ConcurrentHashMap<>();
    }

    public Optional<MailboxPath> getPathToIndex(MailboxPath mailboxPath) {
        if (isPathDeleted.get(mailboxPath) != null) {
            return Optional.absent();
        }
        return Optional.of(
            Optional.fromNullable(nameCorrespondence.get(mailboxPath)).or(mailboxPath));
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
            isPathDeleted.put(event.getMailboxPath(), true);
        } else if (event instanceof MailboxRenamed) {
            nameCorrespondence.put(event.getMailboxPath(), ((MailboxRenamed) event).getNewPath());
        }
    }
}
