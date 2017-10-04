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

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.Flags;
import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

/**
 * JMX managmenent for Mailboxes
 */
public class MailboxManagerManagement extends StandardMBean implements MailboxManagerManagementMBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxManagerManagement.class);

    private MailboxManager mailboxManager;
    private static final boolean RECENT = true;

    @Inject
    public void setMailboxManager(@Named("mailboxmanager") MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    public MailboxManagerManagement() throws NotCompliantMBeanException {
        super(MailboxManagerManagementMBean.class);
    }

    /**
     * @see org.apache.james.adapter.mailbox.MailboxManagerManagementMBean#deleteMailboxes(java.lang.String)
     */
    @Override
    public boolean deleteMailboxes(String username) {
        checkString(username, "Username");
        MailboxSession session = null;
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "deleteMailboxes")
                     .addContext("concernedUser", username)
                     .build()) {
            session = mailboxManager.createSystemSession(username);
            mailboxManager.startProcessingRequest(session);
            List<MailboxMetaData> mList = retrieveAllUserMailboxes(session);
            for (MailboxMetaData aMList : mList) {
                mailboxManager.deleteMailbox(aMList.getPath(), session);
            }
            return true;
        } catch (MailboxException e) {
            LOGGER.error("Error while remove mailboxes for user " + username, e);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            closeSession(session);
        }
        return false;
    }

    /**
     * @see
     * org.apache.james.adapter.mailbox.MailboxManagerManagementMBean#listMailboxes
     * (java.lang.String)
     */
    @Override
    public List<String> listMailboxes(String username) {
        checkString(username, "Username");
        List<String> boxes = new ArrayList<>();
        MailboxSession session = null;
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "listMailboxes")
                     .addContext("concernedUser", username)
                     .build()) {
            session = mailboxManager.createSystemSession(username);
            mailboxManager.startProcessingRequest(session);
            List<MailboxMetaData> mList = retrieveAllUserMailboxes(session);
            boxes = mList.stream()
                .map(aMList -> aMList.getPath().getName())
                .sorted()
                .collect(Guavate.toImmutableList());
        } catch (MailboxException e) {
            LOGGER.error("Error list mailboxes for user " + username, e);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            closeSession(session);
        }
        return boxes;
    }

    @Override
    public MailboxId createMailbox(String namespace, String user, String name) {
        checkMailboxArguments(namespace, user, name);
        MailboxSession session = null;
        MailboxPath mailboxPath = new MailboxPath(namespace, user, name);
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "createMailbox")
                     .addContext("mailboxPath", mailboxPath.asString())
                     .build()) {
            session = mailboxManager.createSystemSession(user);
            mailboxManager.startProcessingRequest(session);
            return mailboxManager.createMailbox(mailboxPath, session)
                .orElseThrow(() -> new MailboxException("mailbox name is probably empty"));
        } catch (Exception e) {
            LOGGER.error("Unable to create mailbox", e);
            throw Throwables.propagate(e);
        } finally {
            closeSession(session);
        }
    }

    @Override
    public void deleteMailbox(String namespace, String user, String name) {
        checkMailboxArguments(namespace, user, name);
        MailboxSession session = null;
        MailboxPath mailboxPath = new MailboxPath(namespace, user, name);
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "deleteMailbox")
                     .addContext("mailboxPath", mailboxPath.asString())
                     .build()) {
            session = mailboxManager.createSystemSession(user);
            mailboxManager.startProcessingRequest(session);
            mailboxManager.deleteMailbox(mailboxPath, session);
        } catch (Exception e) {
            LOGGER.error("Unable to create mailbox", e);
        } finally {
            closeSession(session);
        }
    }

    @Override
    public void importEmlFileToMailbox(String namespace, String user, String name, String emlPath) {
        checkMailboxArguments(namespace, user, name);
        checkString(emlPath, "email file path name");

        MailboxSession session = null;
        MailboxPath mailboxPath = new MailboxPath(namespace, user, name);
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "importEmlFileToMailbox")
                     .addContext("mailboxPath", mailboxPath.asString())
                     .addContext("emlPath", emlPath)
                     .build()) {
            session = mailboxManager.createSystemSession(user);
            mailboxManager.startProcessingRequest(session);
            MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, session);
            InputStream emlFileAsStream = new FileInputStream(emlPath);
            messageManager.appendMessage(emlFileAsStream, new Date(),
                    session, RECENT, new Flags());
        } catch (Exception e) {
            LOGGER.error("Unable to create mailbox", e);
        } finally {
            closeSession(session);
        }
    }

    private void closeSession(MailboxSession session) {
        if (session != null) {
            mailboxManager.endProcessingRequest(session);
            try {
                mailboxManager.logout(session, true);
            } catch (MailboxException e) {
                LOGGER.error("Can not log session out", e);
            }
        }
    }

    private List<MailboxMetaData> retrieveAllUserMailboxes(MailboxSession session) throws MailboxException {
        return mailboxManager.search(
            MailboxQuery.privateMailboxesBuilder(session)
                .matchesAllMailboxNames()
                .build(),
            session);
    }

    private void checkMailboxArguments(String namespace, String user, String name) {
        checkString(namespace, "mailbox path namespace");
        checkString(user, "mailbox path user");
        checkString(name, "mailbox name");
    }

    private void checkString(String argument, String role) {
        Preconditions.checkNotNull(argument, "Provided " + role + " should not be null.");
        Preconditions.checkArgument(!argument.equals(""), "Provided " + role + " should not be empty.");
    }
}