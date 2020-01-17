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

package org.apache.james.transport.mailets.delivery;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.AppendResult;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.server.core.MimeMessageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import reactor.core.publisher.Mono;

public class MailboxAppender {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxAppender.class);

    private final MailboxManager mailboxManager;

    public MailboxAppender(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    public ComposedMessageId append(MimeMessage mail, Username user, String folder) throws MessagingException {
        MailboxSession session = createMailboxSession(user);
        return append(mail, user, useSlashAsSeparator(folder, session), session)
            .getIds();
    }

    private String useSlashAsSeparator(String urlPath, MailboxSession session) throws MessagingException {
        String destination = urlPath.replace('/', session.getPathDelimiter());
        if (Strings.isNullOrEmpty(destination)) {
            throw new MessagingException("Mail can not be delivered to empty folder");
        }
        if (destination.charAt(0) == session.getPathDelimiter()) {
            destination = destination.substring(1);
        }
        return destination;
    }

    private AppendResult append(MimeMessage mail, Username user, String folder, MailboxSession session) throws MessagingException {
        mailboxManager.startProcessingRequest(session);
        try {
            MailboxPath mailboxPath = MailboxPath.forUser(user, folder);
            return appendMessageToMailbox(mail, session, mailboxPath);
        } catch (MailboxException e) {
            throw new MessagingException("Unable to access mailbox.", e);
        } finally {
            closeProcessing(session);
        }
    }

    private AppendResult appendMessageToMailbox(MimeMessage mail, MailboxSession session, MailboxPath path) throws MailboxException, MessagingException {
        createMailboxIfNotExist(session, path);
        final MessageManager mailbox = mailboxManager.getMailbox(path, session);
        if (mailbox == null) {
            throw new MessagingException("Mailbox " + path + " for user " + session.getUser().asString() + " was not found on this server.");
        }
        return mailbox.appendMessage(MessageManager.AppendCommand.builder()
            .recent()
            .build(new MimeMessageInputStream(mail)),
            session);
    }

    private void createMailboxIfNotExist(MailboxSession session, MailboxPath path) throws MailboxException {
        if (!Mono.from(mailboxManager.mailboxExists(path, session)).block()) {
            try {
                mailboxManager.createMailbox(path, session);
            } catch (MailboxExistsException e) {
                LOGGER.info("Mailbox {} have been created concurrently", path);
            }
        }
    }

    public MailboxSession createMailboxSession(Username user) {
        return mailboxManager.createSystemSession(user);
    }

    private void closeProcessing(MailboxSession session) throws MessagingException {
        session.close();
        try {
            mailboxManager.logout(session);
        } finally {
            mailboxManager.endProcessingRequest(session);
        }
    }

}
