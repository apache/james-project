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

package org.apache.james.pop3server.core;

import java.io.IOException;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.pop3server.mailbox.MailboxAdapterFactory;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.mailbox.Mailbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class Pop3MailboxProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(Pop3MailboxProvider.class);

    private final MailboxManager mailboxManager;
    private final MailboxAdapterFactory mailboxAdapterFactory;

    public Pop3MailboxProvider(MailboxManager mailboxManager, MailboxAdapterFactory mailboxAdapterFactory) {
        this.mailboxManager = mailboxManager;
        this.mailboxAdapterFactory = mailboxAdapterFactory;
    }

    public Mailbox open(POP3Session session, Username username) throws IOException {
        MailboxSession mailboxSession = null;
        try {
            // Credentials have already been verified by the selected SASL mechanism.
            mailboxSession = mailboxManager.authenticate(username).withoutDelegation();
            session.stopDetectingCommandInjection();
            mailboxManager.startProcessingRequest(mailboxSession);
            MailboxPath inbox = MailboxPath.inbox(mailboxSession);

            if (!Mono.from(mailboxManager.mailboxExists(inbox, mailboxSession)).block()) {
                Optional<MailboxId> mailboxId = mailboxManager.createMailbox(inbox, mailboxSession);
                LOGGER.info("Provisioning INBOX. {} created.", mailboxId);
            }
            MessageManager mailbox = mailboxManager.getMailbox(inbox, mailboxSession);
            LOGGER.info("Opening mailbox {} {} with mailbox session {}",
                mailbox.getId().serialize(),
                mailbox.getMailboxPath().asString(),
                mailboxSession.getSessionId().getValue());
            return mailboxAdapterFactory.create(mailbox, mailboxSession);
        } catch (MailboxException e) {
            throw new IOException("Unable to access mailbox for user " + username.asString(), e);
        } finally {
            if (mailboxSession != null) {
                mailboxManager.endProcessingRequest(mailboxSession);
            }
        }
    }
}
