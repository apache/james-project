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

package org.apache.james.mailbox.manager;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMail;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.MaxQuotaManager;

/**
 * Provide an initialized Mailbox environment where we can run managers tests
 */
public class ManagerTestProvisionner {
    public static final Username USER = Username.of("user@domain.org");
    public static final String USER_PASS = "pass";
    public static final Username OTHER_USER = Username.of("otherUser@domain.org");
    public static final String OTHER_USER_PASS = "otherPass";
    public static final MailboxPath INBOX = MailboxPath.inbox(USER);

    private IntegrationResources<?> integrationResources;

    private MessageManager messageManager;
    private MailboxPath subFolder;
    private MailboxSession session;


    public ManagerTestProvisionner(IntegrationResources<?> integrationResources) throws Exception {
        this.integrationResources = integrationResources;

        session = integrationResources.getMailboxManager().authenticate(USER, USER_PASS).withoutDelegation();
        subFolder = new MailboxPath(INBOX, "INBOX.SUB");

        MaxQuotaManager maxQuotaManager = integrationResources.getMaxQuotaManager();
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(1000));
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(1000000));
    }

    public void createMailboxes() throws MailboxException {
        integrationResources.getMailboxManager().createMailbox(INBOX, session);
        integrationResources.getMailboxManager().createMailbox(subFolder, session);
        messageManager = integrationResources.getMailboxManager().getMailbox(INBOX, session);
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public MailboxPath getSubFolder() {
        return subFolder;
    }

    public MailboxSession getSession() {
        return session;
    }

    public void fillMailbox() throws MailboxException, UnsupportedEncodingException {
        for (int i = 0; i < 4; i++) {
            provideSomeMessages();
        }
    }

    private void provideSomeMessages() throws MailboxException, UnsupportedEncodingException {
        appendMessage(messageManager, session, new FlagsBuilder().add(Flags.Flag.SEEN).build());
        appendMessage(messageManager, session, new FlagsBuilder().add(Flags.Flag.DELETED).build());
        appendMessage(messageManager, session, new FlagsBuilder().build());
        appendMessage(messageManager, session, new FlagsBuilder().add(Flags.Flag.RECENT).build());
    }

    public MessageUid appendMessage(MessageManager messageManager, MailboxSession session, Flags flags) throws MailboxException, UnsupportedEncodingException {
        return messageManager.appendMessage(new ByteArrayInputStream(MockMail.MAIL_TEXT_PLAIN.getBytes(StandardCharsets.UTF_8)),
            Calendar.getInstance().getTime(), session, true, flags)
            .getId()
            .getUid();
    }

}