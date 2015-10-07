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

import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMail;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Flags;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;

/**
 * Provide an initialized Mailbox environment where we can run managers tests
 */
public class ManagerTestResources {

    private static final Logger LOG = LoggerFactory.getLogger(ManagerTestResources.class);

    public static final String USER = "user@domain.org";
    public static final String USER_PASS = "pass";

    private MailboxManager mailboxManager;

    private MailboxPath inbox;
    private MessageManager messageManager;
    private MailboxPath subFolder;

    private MailboxSession session;

    private MaxQuotaManager maxQuotaManager;
    private QuotaManager quotaManager;
    private GroupMembershipResolver groupMembershipResolver;
    private QuotaRootResolver quotaRootResolver;

    private IntegrationResources integrationResources;

    public ManagerTestResources(IntegrationResources integrationResources) throws Exception {
        this.integrationResources = integrationResources;
        maxQuotaManager = integrationResources.createMaxQuotaManager();
        groupMembershipResolver = integrationResources.createGroupMembershipResolver();
        mailboxManager = integrationResources.createMailboxManager(groupMembershipResolver);
        quotaRootResolver = integrationResources.createQuotaRootResolver(mailboxManager);
        quotaManager = integrationResources.createQuotaManager(maxQuotaManager, mailboxManager);
        integrationResources.init();
        session = mailboxManager.login(USER, USER_PASS, LOG);
        inbox = MailboxPath.inbox(session);
        subFolder = new MailboxPath(inbox, "INBOX.SUB");

        maxQuotaManager.setDefaultMaxMessage(1000);
        maxQuotaManager.setDefaultMaxStorage(1000000);
    }

    public void createMailboxes() throws MailboxException {
        mailboxManager.createMailbox(inbox, session);
        mailboxManager.createMailbox(subFolder, session);
        messageManager = mailboxManager.getMailbox(inbox, session);
    }

    public GroupMembershipResolver getGroupMembershipResolver() {
        return groupMembershipResolver;
    }

    public QuotaManager getQuotaManager() {
        return quotaManager;
    }

    public MaxQuotaManager getMaxQuotaManager() {
        return maxQuotaManager;
    }

    public QuotaRootResolver getQuotaRootResolver() {
        return quotaRootResolver;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public MailboxPath getSubFolder() {
        return subFolder;
    }

    public MailboxPath getInbox() {
        return inbox;
    }

    public MailboxManager getMailboxManager() {
        return mailboxManager;
    }

    public MailboxSession getSession() {
        return session;
    }

    public IntegrationResources getIntegrationResources() {
        return integrationResources;
    }

    public void fillMailbox() throws MailboxException, UnsupportedEncodingException {
        for(int i = 0; i < 4; i++) {
            provideSomeMessages();
        }
    }

    private void provideSomeMessages() throws MailboxException, UnsupportedEncodingException {
        appendMessage(messageManager, session, new FlagsBuilder().add(Flags.Flag.SEEN).build());
        appendMessage(messageManager, session, new FlagsBuilder().add(Flags.Flag.DELETED).build());
        appendMessage(messageManager, session, new FlagsBuilder().build());
        appendMessage(messageManager, session, new FlagsBuilder().add(Flags.Flag.RECENT).build());
    }

    public long appendMessage(MessageManager messageManager, MailboxSession session, Flags flags) throws MailboxException, UnsupportedEncodingException {
        return messageManager.appendMessage(new ByteArrayInputStream(MockMail.MAIL_TEXT_PLAIN.getBytes("UTF-8")),
            Calendar.getInstance().getTime(), session, true, flags);
    }

}