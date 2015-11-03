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

package org.apache.james.modules.server;

import org.apache.james.adapter.mailbox.MailboxCopierManagement;
import org.apache.james.adapter.mailbox.MailboxCopierManagementMBean;
import org.apache.james.adapter.mailbox.MailboxManagerManagement;
import org.apache.james.adapter.mailbox.MailboxManagerManagementMBean;
import org.apache.james.adapter.mailbox.MailboxManagerResolver;
import org.apache.james.domainlist.api.DomainListManagementMBean;
import org.apache.james.domainlist.lib.DomainListManagement;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.copier.MailboxCopier;
import org.apache.james.mailbox.copier.MailboxCopierImpl;
import org.apache.james.mailetcontainer.api.jmx.MailSpoolerMBean;
import org.apache.james.mailetcontainer.impl.JamesMailSpooler;
import org.apache.james.rrt.api.RecipientRewriteTableManagementMBean;
import org.apache.james.rrt.lib.RecipientRewriteTableManagement;
import org.apache.james.user.api.UsersRepositoryManagementMBean;
import org.apache.james.user.lib.UsersRepositoryManagement;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.GuiceMailboxManagerResolver;
import org.apache.james.utils.MailboxManagerDefinition;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class JMXServerModule extends AbstractModule {

    private static final String JMX_COMPONENT_DOMAINLIST = "org.apache.james:type=component,name=domainlist";
    private static final String JMX_COMPONENT_USERS_REPOSITORY = "org.apache.james:type=component,name=usersrepository";
    private static final String JMX_COMPONENT_RECIPIENTREWRITETABLE = "org.apache.james:type=component,name=recipientrewritetable";
    private static final String JMX_COMPONENT_NAME_MAILBOXMANAGERBEAN = "org.apache.james:type=component,name=mailboxmanagerbean";
    private static final String JMX_COMPONENT_MAILBOXCOPIER = "org.apache.james:type=component,name=mailboxcopier";

    @Override
    protected void configure() {
        bind(MailboxCopier.class).annotatedWith(Names.named("mailboxcopier")).to(MailboxCopierImpl.class);
        bind(MailboxCopierManagementMBean.class).to(MailboxCopierManagement.class);
        bind(MailboxManagerResolver.class).to(GuiceMailboxManagerResolver.class);
        bind(DomainListManagementMBean.class).to(DomainListManagement.class);
        bind(UsersRepositoryManagementMBean.class).to(UsersRepositoryManagement.class);
        bind(MailboxManagerManagementMBean.class).to(MailboxManagerManagement.class);
        bind(RecipientRewriteTableManagementMBean.class).to(RecipientRewriteTableManagement.class);
        bind(MailSpoolerMBean.class).to(JamesMailSpooler.class);
        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(JMXModuleConfigurationPerformer.class);
        Multibinder.newSetBinder(binder(), MailboxManagerDefinition.class).addBinding().to(CassandraMailboxManagerDefinition.class);
    }

    @Singleton
    private static class CassandraMailboxManagerDefinition extends MailboxManagerDefinition {
        @Inject
        private CassandraMailboxManagerDefinition(CassandraMailboxManager manager) {
            super("cassandra-mailboxmanager", manager);
        }
    }
    
    @Singleton
    public static class JMXModuleConfigurationPerformer implements ConfigurationPerformer {

        private final JMXServer jmxServer;
        private final DomainListManagementMBean domainListManagementMBean;
        private final UsersRepositoryManagementMBean usersRepositoryManagementMBean;
        private final RecipientRewriteTableManagementMBean recipientRewriteTableManagementMBean;
        private final MailboxManagerManagementMBean mailboxManagerManagementMBean;
        private final MailboxCopierManagementMBean mailboxCopierManagementMBean;

        @Inject
        public JMXModuleConfigurationPerformer(JMXServer jmxServer,
                                               DomainListManagementMBean domainListManagementMBean,
                                               UsersRepositoryManagementMBean usersRepositoryManagementMBean,
                                               RecipientRewriteTableManagementMBean recipientRewriteTableManagementMBean,
                                               MailboxManagerManagementMBean mailboxManagerManagementMBean,
                                               MailboxCopierManagementMBean mailboxCopierManagementMBean) {
            this.jmxServer = jmxServer;
            this.domainListManagementMBean = domainListManagementMBean;
            this.usersRepositoryManagementMBean = usersRepositoryManagementMBean;
            this.recipientRewriteTableManagementMBean = recipientRewriteTableManagementMBean;
            this.mailboxManagerManagementMBean = mailboxManagerManagementMBean;
            this.mailboxCopierManagementMBean = mailboxCopierManagementMBean;
        }

        @Override
        public void initModule() throws Exception {
            jmxServer.start();
            jmxServer.register(JMX_COMPONENT_DOMAINLIST, domainListManagementMBean);
            jmxServer.register(JMX_COMPONENT_USERS_REPOSITORY, usersRepositoryManagementMBean);
            jmxServer.register(JMX_COMPONENT_RECIPIENTREWRITETABLE, recipientRewriteTableManagementMBean);
            jmxServer.register(JMX_COMPONENT_NAME_MAILBOXMANAGERBEAN, mailboxManagerManagementMBean);
            jmxServer.register(JMX_COMPONENT_MAILBOXCOPIER, mailboxCopierManagementMBean);
        }
    }

}
