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

package org.apache.james.cli.probe.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;

import javax.mail.Flags;
import javax.management.MalformedObjectNameException;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.adapter.mailbox.MailboxCopierManagementMBean;
import org.apache.james.adapter.mailbox.MailboxManagerManagementMBean;
import org.apache.james.adapter.mailbox.ReIndexerManagementMBean;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.probe.MailboxProbe;

public class JmxMailboxProbe implements MailboxProbe, JmxProbe {

    private final static String MAILBOXCOPIER_OBJECT_NAME = "org.apache.james:type=component,name=mailboxcopier";
    private final static String MAILBOXMANAGER_OBJECT_NAME = "org.apache.james:type=component,name=mailboxmanagerbean";
    private final static String REINDEXER_OBJECT_NAME = "org.apache.james:type=component,name=reindexerbean";

    private MailboxCopierManagementMBean mailboxCopierManagement;
    private MailboxManagerManagementMBean mailboxManagerManagement;
    private ReIndexerManagementMBean reIndexerManagement;

    public JmxMailboxProbe connect(JmxConnection jmxc) throws IOException {
        try {
            mailboxCopierManagement = jmxc.retrieveBean(MailboxCopierManagementMBean.class, MAILBOXCOPIER_OBJECT_NAME);
            mailboxManagerManagement = jmxc.retrieveBean(MailboxManagerManagementMBean.class, MAILBOXMANAGER_OBJECT_NAME);
            reIndexerManagement = jmxc.retrieveBean(ReIndexerManagementMBean.class, REINDEXER_OBJECT_NAME);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException("Invalid ObjectName? Please report this as a bug.", e);
        }
        return this;
    }


    @Override
    public void copyMailbox(String srcBean, String dstBean) throws Exception {
        mailboxCopierManagement.copy(srcBean, dstBean);
    }

    @Override
    public void deleteUserMailboxesNames(String user) throws Exception {
        mailboxManagerManagement.deleteMailboxes(user);
    }

    @Override
    public MailboxId createMailbox(String namespace, String user, String name) {
        return mailboxManagerManagement.createMailbox(namespace, user, name);
    }

    @Override
    public Collection<String> listUserMailboxes(String user) {
        return mailboxManagerManagement.listMailboxes(user);
    }

    @Override
    public void deleteMailbox(String namespace, String user, String name) {
        mailboxManagerManagement.deleteMailbox(namespace, user, name);
    }

    @Override
    public void importEmlFileToMailbox(String namespace, String user, String name, String emlpath) {
        mailboxManagerManagement.importEmlFileToMailbox(namespace, user, name, emlpath);
    }

    @Override
    public void reIndexMailbox(String namespace, String user, String name) throws Exception {
        reIndexerManagement.reIndex(namespace, user, name);
    }

    @Override
    public void reIndexAll() throws Exception {
        reIndexerManagement.reIndex();
    }

    @Override
    public Mailbox getMailbox(String namespace, String user, String name) {
        throw new NotImplementedException();
    }

    @Override
    public ComposedMessageId appendMessage(String username, MailboxPath mailboxPath, InputStream message,
            Date internalDate, boolean isRecent, Flags flags) throws MailboxException {
        throw new NotImplementedException();
    }


    @Override
    public Collection<String> listSubscriptions(String user) throws Exception {
        throw new NotImplementedException();
    }
}