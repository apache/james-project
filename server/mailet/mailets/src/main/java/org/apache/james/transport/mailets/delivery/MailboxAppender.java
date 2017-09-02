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

import java.util.Date;

import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MimeMessageInputStream;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;

import com.google.common.base.Strings;

public class MailboxAppender {
    private static final boolean IS_RECENT = true;
    private static final Flags FLAGS = null;

    private final MailboxManager mailboxManager;

    public MailboxAppender(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    public void append(MimeMessage mail, String user, String folder) throws MessagingException {
        MailboxSession session = createMailboxSession(user);
        append(mail, user, useSlashAsSeparator(folder, session), session);
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

    private void append(MimeMessage mail, String user, String folder, MailboxSession session) throws MessagingException {
        mailboxManager.startProcessingRequest(session);
        try {
            MailboxPath mailboxPath = new MailboxPath(session.getPersonalSpace(), user, folder);
            appendMessageToMailbox(mail, session, mailboxPath);
        } catch (MailboxException e) {
            throw new MessagingException("Unable to access mailbox.", e);
        } finally {
            closeProcessing(session);
        }
    }

    private void appendMessageToMailbox(MimeMessage mail, MailboxSession session, MailboxPath path) throws MailboxException, MessagingException {
        createMailboxIfNotExist(session, path);
        final MessageManager mailbox = mailboxManager.getMailbox(path, session);
        if (mailbox == null) {
            throw new MessagingException("Mailbox " + path + " for user " + session.getUser().getUserName() + " was not found on this server.");
        }
        mailbox.appendMessage(new MimeMessageInputStream(mail), new Date(), session, IS_RECENT, FLAGS);
    }

    private void createMailboxIfNotExist(MailboxSession session, MailboxPath path) throws MailboxException {
        if (!mailboxManager.mailboxExists(path, session)) {
            mailboxManager.createMailbox(path, session);
        }
    }

    public MailboxSession createMailboxSession(String user) throws MessagingException {
        try {
            return mailboxManager.createSystemSession(user);
        } catch (BadCredentialsException e) {
            throw new MessagingException("Unable to authenticate to mailbox", e);
        } catch (MailboxException e) {
            throw new MessagingException("Can not access mailbox", e);
        }
    }

    private void closeProcessing(MailboxSession session) throws MessagingException {
        session.close();
        try {
            try {
                mailboxManager.logout(session, true);
            } catch (MailboxException e) {
                throw new MessagingException("Can logout from mailbox", e);
            }
        } finally {
            mailboxManager.endProcessingRequest(session);
        }
    }

}
