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
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.lmtpserver.DataLineLMTPHandler;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.model.Content;
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
import org.apache.james.util.AuditTrail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

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
            Username username = users.getUsername(recipient);

            MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
            MailboxPath inbox = MailboxPath.inbox(mailboxSession);

            mailboxManager.startProcessingRequest(mailboxSession);

            // create inbox if not exist
            if (Boolean.FALSE.equals(Mono.from(mailboxManager.mailboxExists(inbox, mailboxSession)).block())) {
                Optional<MailboxId> mailboxId = mailboxManager.createMailbox(inbox, mailboxSession);
                LOGGER.info("Provisioning INBOX. {} created.", mailboxId);
            }
            mailboxManager.getMailbox(MailboxPath.inbox(username), mailboxSession)
                .appendMessage(MessageManager.AppendCommand.builder()
                    .recent()
                    .build(new Content() {
                        @Override
                        public InputStream getInputStream() throws IOException {
                            return envelope.getMessageInputStream();
                        }

                        @Override
                        public long size() {
                            return envelope.getSize();
                        }
                    }),
                    mailboxSession);
            mailboxManager.endProcessingRequest(mailboxSession);
            auditTrail(session, recipient, envelope);
            return HookResult.builder()
                .hookReturnCode(HookReturnCode.ok())
                .smtpReturnCode(SMTPRetCode.MAIL_OK)
                .smtpDescription(DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.CONTENT_OTHER) + " Message received <" + recipient.asString() + ">")
                .build();
        } catch (OverQuotaException e) {
            LOGGER.info("{} is over quota", recipient);
            return HookResult.builder()
                .hookReturnCode(HookReturnCode.denySoft())
                .smtpReturnCode(SMTPRetCode.QUOTA_EXCEEDED)
                .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.MAILBOX_FULL) + " Over Quota error when delivering message to <" + recipient + ">")
                .build();
        } catch (MailboxException | UsersRepositoryException e) {
            LOGGER.error("Unexpected error handling DATA stream", e);
            return HookResult.builder()
                .hookReturnCode(HookReturnCode.denySoft())
                .smtpDescription(" Temporary error deliver message to " + recipient)
                .build();
        }
    }

    private void auditTrail(SMTPSession session, MailAddress recipient, MailEnvelope envelope) {
        if (envelope instanceof DataLineLMTPHandler.ReadOnlyMailEnvelope) {
            DataLineLMTPHandler.ReadOnlyMailEnvelope readOnlyMailEnvelope = (DataLineLMTPHandler.ReadOnlyMailEnvelope) envelope;
            AuditTrail.entry()
                .username(() -> Optional.ofNullable(session.getUsername())
                    .map(Username::asString)
                    .orElse(""))
                .remoteIP(() -> Optional.ofNullable(session.getRemoteAddress()))
                .sessionId(session::getSessionID)
                .protocol("LMTP")
                .action("Deliver mail")
                .parameters(() -> Map.of("mailId", readOnlyMailEnvelope.getMailId(),
                    "mimeMessageId", readOnlyMailEnvelope.getMimeMessageId().orElse(""),
                    "sender", readOnlyMailEnvelope.getMaybeSender().asString(),
                    "recipient", recipient.asString()))
                .log("LMTP mail sent.");
        }
    }
}
