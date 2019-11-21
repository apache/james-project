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

package org.apache.james.lmtpserver.hook;

import java.io.IOException;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.protocols.lmtp.hook.DeliverToRecipientHook;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DeliverToRecipientHook} which deliver the message directly to the recipients mailbox.
 */
public class MailboxDeliverToRecipientHandler implements DeliverToRecipientHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxDeliverToRecipientHandler.class);
  
    private final UsersRepository users;
    private final MailboxManager mailboxManager;

    @Inject
    public MailboxDeliverToRecipientHandler(UsersRepository users, @Named("mailboxmanager") MailboxManager mailboxManager) {
        this.users = users;
        this.mailboxManager = mailboxManager;
    }

    @Override
    public HookResult deliver(SMTPSession session, MailAddress recipient, MailEnvelope envelope) {
        try {
            Username username = users.getUser(recipient);

            MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
            MailboxPath inbox = MailboxPath.inbox(mailboxSession);

            mailboxManager.startProcessingRequest(mailboxSession);

            // create inbox if not exist
            if (!mailboxManager.mailboxExists(inbox, mailboxSession)) {
                Optional<MailboxId> mailboxId = mailboxManager.createMailbox(inbox, mailboxSession);
                LOGGER.info("Provisioning INBOX. {} created.", mailboxId);
            }
            mailboxManager.getMailbox(MailboxPath.inbox(username), mailboxSession)
                .appendMessage(MessageManager.AppendCommand.builder()
                    .recent()
                    .build(envelope.getMessageInputStream()),
                    mailboxSession);
            mailboxManager.endProcessingRequest(mailboxSession);
            return HookResult.builder()
                .hookReturnCode(HookReturnCode.ok())
                .smtpReturnCode(SMTPRetCode.MAIL_OK)
                .smtpDescription(DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.CONTENT_OTHER) + " Message received")
                .build();
        } catch (IOException | MailboxException | UsersRepositoryException e) {
            LOGGER.error("Unexpected error handling DATA stream", e);
            return HookResult.builder()
                .hookReturnCode(HookReturnCode.denySoft())
                .smtpDescription(" Temporary error deliver message to " + recipient)
                .build();
        }
    }
}
