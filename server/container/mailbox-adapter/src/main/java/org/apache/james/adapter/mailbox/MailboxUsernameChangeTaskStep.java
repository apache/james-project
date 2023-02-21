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

package org.apache.james.adapter.mailbox;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.reactivestreams.Publisher;

public class MailboxUsernameChangeTaskStep implements UsernameChangeTaskStep {
    private final StoreMailboxManager mailboxManager;

    @Inject
    public MailboxUsernameChangeTaskStep(StoreMailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    @Override
    public StepName name() {
        return new StepName("MailboxUsernameChangeTaskStep");
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public Publisher<Void> changeUsername(Username oldUsername, Username newUsername) {
        MailboxSession fromSession = mailboxManager.createSystemSession(oldUsername);
        MailboxSession toSession = mailboxManager.createSystemSession(newUsername);

        MailboxQuery queryUser = MailboxQuery.builder()
            .privateNamespace()
            .user(fromSession.getUser())
            .build();

        return mailboxManager.search(queryUser, MailboxManager.MailboxSearchFetchType.Minimal, fromSession)
            // Only keep top level, rename takes care of sub mailboxes
            .filter(mailbox -> mailbox.getPath().getHierarchyLevels(fromSession.getPathDelimiter()).size() == 1)
            .concatMap(mailbox -> mailboxManager.renameMailboxReactive(mailbox.getPath(), mailbox.getPath().withUser(newUsername),
                MailboxManager.RenameOption.RENAME_SUBSCRIPTIONS,
                fromSession, toSession))
            .then();
    }
}
