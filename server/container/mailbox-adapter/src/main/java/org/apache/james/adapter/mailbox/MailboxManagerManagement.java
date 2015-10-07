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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;
import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import com.google.common.base.Preconditions;
import org.apache.james.lifecycle.api.LogEnabled;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.slf4j.Logger;

/**
 * JMX managmenent for Mailboxes
 */
public class MailboxManagerManagement extends StandardMBean implements MailboxManagerManagementMBean, LogEnabled {

    private MailboxManager mailboxManager;
    private Logger log;

    @Inject
    @Resource(name = "mailboxmanager")
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
        try {
            session = mailboxManager.createSystemSession(username, log);
            mailboxManager.startProcessingRequest(session);
            List<MailboxMetaData> mList = retrieveAllUserMailboxes(username, session);
            for (MailboxMetaData aMList : mList) {
                mailboxManager.deleteMailbox(aMList.getPath(), session);
            }
            return true;
        } catch (MailboxException e) {
            log.error("Error while remove mailboxes for user " + username, e);
        } finally {
            closeSession(session);
        }
        return false;
    }

    /**
     * @see org.apache.james.lifecycle.api.LogEnabled#setLog(org.slf4j.Logger)
     */
    @Override
    public void setLog(Logger log) {
        this.log = log;
    }

    /**
     * @see
     * org.apache.james.adapter.mailbox.MailboxManagerManagementMBean#listMailboxes
     * (java.lang.String)
     */
    @Override
    public List<String> listMailboxes(String username) {
        checkString(username, "Username");
        List<String> boxes = new ArrayList<String>();
        MailboxSession session = null;
        try {
            session = mailboxManager.createSystemSession(username, log);
            mailboxManager.startProcessingRequest(session);
            List<MailboxMetaData> mList = retrieveAllUserMailboxes(username, session);
            for (MailboxMetaData aMList : mList) {
                boxes.add(aMList.getPath().getName());
            }
            Collections.sort(boxes);
        } catch (MailboxException e) {
            log.error("Error list mailboxes for user " + username, e);
        } finally {
            closeSession(session);
        }
        return boxes;
    }

    @Override
    public void createMailbox(String namespace, String user, String name) {
        checkMailboxArguments(namespace, user, name);
        MailboxSession session = null;
        try {
            session = mailboxManager.createSystemSession(user, log);
            mailboxManager.startProcessingRequest(session);
            mailboxManager.createMailbox(new MailboxPath(namespace, user, name), session);
        } catch (Exception e) {
            log.error("Unable to create mailbox", e);
        } finally {
            closeSession(session);
        }
    }

    @Override
    public void deleteMailbox(String namespace, String user, String name) {
        checkMailboxArguments(namespace, user, name);
        MailboxSession session = null;
        try {
            session = mailboxManager.createSystemSession(user, log);
            mailboxManager.startProcessingRequest(session);
            mailboxManager.deleteMailbox(new MailboxPath(namespace, user, name), session);
        } catch (Exception e) {
            log.error("Unable to create mailbox", e);
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
                log.error("Can not log session out", e);
            }
        }
    }

    private List<MailboxMetaData> retrieveAllUserMailboxes(String username, MailboxSession session) throws MailboxException {
        return mailboxManager.search(
            new MailboxQuery(new MailboxPath(MailboxConstants.USER_NAMESPACE, username, ""),
                "*",
                session.getPathDelimiter()),
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