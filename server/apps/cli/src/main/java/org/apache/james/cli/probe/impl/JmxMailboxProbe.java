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
import java.util.Collection;

import javax.management.MalformedObjectNameException;

import org.apache.james.adapter.mailbox.MailboxCopierManagementMBean;
import org.apache.james.adapter.mailbox.MailboxManagerManagementMBean;
import org.apache.james.adapter.mailbox.ReIndexerManagementMBean;
import org.apache.james.mailbox.model.MailboxId;

public class JmxMailboxProbe implements JmxProbe {
    private static final String MAILBOXCOPIER_OBJECT_NAME = "org.apache.james:type=component,name=mailboxcopier";
    private static final String MAILBOXMANAGER_OBJECT_NAME = "org.apache.james:type=component,name=mailboxmanagerbean";
    private static final String REINDEXER_OBJECT_NAME = "org.apache.james:type=component,name=reindexerbean";

    private MailboxCopierManagementMBean mailboxCopierManagement;
    private MailboxManagerManagementMBean mailboxManagerManagement;
    private ReIndexerManagementMBean reIndexerManagement;

    @Override
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

    public void copyMailbox(String srcBean, String dstBean) throws Exception {
        mailboxCopierManagement.copy(srcBean, dstBean);
    }

    public void deleteUserMailboxesNames(String user) throws Exception {
        mailboxManagerManagement.deleteMailboxes(user);
    }

    public MailboxId createMailbox(String namespace, String user, String name) {
        return mailboxManagerManagement.createMailbox(namespace, user, name);
    }

    public Collection<String> listUserMailboxes(String user) {
        return mailboxManagerManagement.listMailboxes(user);
    }

    public void deleteMailbox(String namespace, String user, String name) {
        mailboxManagerManagement.deleteMailbox(namespace, user, name);
    }

    public void importEmlFileToMailbox(String namespace, String user, String name, String emlpath) {
        mailboxManagerManagement.importEmlFileToMailbox(namespace, user, name, emlpath);
    }

    public void reIndexMailbox(String namespace, String user, String name) throws Exception {
        reIndexerManagement.reIndex(namespace, user, name);
    }

    public void reIndexAll() throws Exception {
        reIndexerManagement.reIndex();
    }
}