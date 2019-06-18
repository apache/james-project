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
package org.apache.james.mailbox.inmemory;

import java.util.Optional;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.Wildcard;
import org.apache.james.mailbox.store.MessageManagerTestSystem;

public class InMemoryMessageManagerTestSystem extends MessageManagerTestSystem {

    private static final int UID_VALIDITY = 1024;

    private final MailboxManager mailboxManager;

    public InMemoryMessageManagerTestSystem(MailboxManager mailboxManager) {
        super(mailboxManager);
        this.mailboxManager = mailboxManager;
    }

    @Override
    public Mailbox createMailbox(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
        return new Mailbox(mailboxPath, UID_VALIDITY, messageManager.getId());
    }

    @Override
    public void deleteMailbox(final MailboxId mailboxId, MailboxSession session) {
        try {
            Optional<MailboxMetaData> mailbox = retrieveMailbox(mailboxId, session);
            if (mailbox.isPresent()) {
                mailboxManager.deleteMailbox(mailbox.get().getPath(), session);
            }
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<MailboxMetaData> retrieveMailbox(final MailboxId mailboxId, MailboxSession mailboxSession) throws MailboxException {
        MailboxQuery userMailboxesQuery = MailboxQuery.privateMailboxesBuilder(mailboxSession)
            .expression(Wildcard.INSTANCE)
            .build();
        return mailboxManager.search(userMailboxesQuery, mailboxSession)
            .stream()
            .filter(mailboxMetaData -> mailboxMetaData.getId().equals(mailboxId))
            .findFirst();
    }
}
