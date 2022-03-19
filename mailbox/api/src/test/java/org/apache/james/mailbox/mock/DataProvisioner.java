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
package org.apache.james.mailbox.mock;

import java.util.stream.IntStream;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;

public class DataProvisioner {
    
    /**
     * Number of Domains to be created in the Mailbox Manager.
     */
    public static final int DOMAIN_COUNT = 3;
    
    /**
     * Number of Users (with INBOX) to be created in the Mailbox Manager.
     */
    public static final int USER_COUNT = 3;
    
    /**
     * Number of Sub Mailboxes (mailbox in INBOX) to be created in the Mailbox Manager.
     */
    public static final int SUB_MAILBOXES_COUNT = 3;
    
    /**
     * Number of Sub Sub Mailboxes (mailbox in a mailbox under INBOX) to be created in the Mailbox Manager.
     */
    public static final int SUB_SUB_MAILBOXES_COUNT = 3;
    
    /**
     * The expected Mailboxes count calculated based on the feeded mails.
     */
    public static final int EXPECTED_MAILBOXES_COUNT = DOMAIN_COUNT * 
                     (USER_COUNT + // INBOX
                      USER_COUNT * SUB_MAILBOXES_COUNT + // INBOX.SUB_FOLDER
                      USER_COUNT * SUB_MAILBOXES_COUNT * SUB_SUB_MAILBOXES_COUNT);  // INBOX.SUB_FOLDER.SUBSUB_FOLDER
    
    /**
     * Number of Messages per Mailbox to be created in the Mailbox Manager.
     */
    public static final int MESSAGE_PER_MAILBOX_COUNT = 3;
    
    /**
     * Utility method to feed the Mailbox Manager with a number of 
     * mailboxes and messages per mailbox.
     */
    public static void feedMailboxManager(MailboxManager mailboxManager) {
        IntStream.range(0, DOMAIN_COUNT)
            .mapToObj(i -> "localhost" + i)
            .forEach(domain -> provisionDomain(mailboxManager, domain));
    }

    public static void provisionDomain(MailboxManager mailboxManager, String domain) {
        IntStream.range(0, USER_COUNT)
            .mapToObj(i -> "user" + i + "@" + domain)
            .map(Username::of)
            .forEach(user -> provisionUser(mailboxManager, user));
    }

    private static void provisionUser(MailboxManager mailboxManager, Username user) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(user);
        mailboxManager.startProcessingRequest(mailboxSession);

        createMailbox(mailboxManager, mailboxSession, MailboxPath.inbox(mailboxSession));

        IntStream.range(0, SUB_MAILBOXES_COUNT)
            .mapToObj(i -> MailboxConstants.INBOX + ".SUB_FOLDER_" + i)
            .peek(name -> createMailbox(mailboxManager, mailboxSession, MailboxPath.forUser(user, name)))
            .forEach(name ->  createSubSubMailboxes(mailboxManager, mailboxSession, name));

        mailboxManager.endProcessingRequest(mailboxSession);
    }

    private static void createSubSubMailboxes(MailboxManager mailboxManager,MailboxSession mailboxSession, String subFolderName) {
        IntStream.range(0, SUB_SUB_MAILBOXES_COUNT)
            .mapToObj(i -> subFolderName + ".SUBSUB_FOLDER_" + i)
            .forEach(name -> createMailbox(mailboxManager, mailboxSession, MailboxPath.forUser(mailboxSession.getUser(), name)));

    }

    private static void createMailbox(MailboxManager mailboxManager, MailboxSession mailboxSession, MailboxPath mailboxPath) {
        try {
            mailboxManager.createMailbox(mailboxPath, mailboxSession);
            MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);

            IntStream.range(0, MESSAGE_PER_MAILBOX_COUNT)
                .forEach(i -> appendMessage(messageManager, mailboxSession));
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    private static void appendMessage(MessageManager messageManager, MailboxSession mailboxSession) {
        try {
            messageManager.appendMessage(
                MessageManager.AppendCommand.builder()
                    .recent()
                    .withFlags(new Flags(Flags.Flag.RECENT))
                    .build(MockMail.MAIL_TEXT_PLAIN),
                mailboxSession);
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }
}
