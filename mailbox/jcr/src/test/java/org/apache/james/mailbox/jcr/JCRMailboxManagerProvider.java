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

package org.apache.james.mailbox.jcr;

import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jcr.mail.JCRModSeqProvider;
import org.apache.james.mailbox.jcr.mail.JCRUidProvider;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.xml.sax.InputSource;

import com.google.common.base.Throwables;

public class JCRMailboxManagerProvider {
    public static final String JACKRABBIT_HOME = "target/jackrabbit";

    public static RepositoryImpl createRepository() {
        RepositoryConfig config;
        try {
            config = RepositoryConfig.create(new InputSource(JCRMailboxManagerTest.class.getClassLoader().getResourceAsStream("test-repository.xml")), JACKRABBIT_HOME);
            return RepositoryImpl.create(config);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static JCRMailboxManager provideMailboxManager(String user, String pass, String workspace, RepositoryImpl repository) {
        JCRUtils.registerCnd(repository, workspace, user, pass);
        MailboxSessionJCRRepository sessionRepos = new GlobalMailboxSessionJCRRepository(repository, workspace, user, pass);
        JVMMailboxPathLocker locker = new JVMMailboxPathLocker();
        JCRUidProvider uidProvider = new JCRUidProvider(locker, sessionRepos);
        JCRModSeqProvider modSeqProvider = new JCRModSeqProvider(locker, sessionRepos);
        JCRMailboxSessionMapperFactory mf = new JCRMailboxSessionMapperFactory(sessionRepos, uidProvider, modSeqProvider);

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();

        Authenticator noAuthenticator = null;
        Authorizator noAuthorizator = null;
        StoreRightManager storeRightManager = new StoreRightManager(mf, aclResolver, groupMembershipResolver);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mf, storeRightManager);
        DefaultDelegatingMailboxListener delegatingListener = new DefaultDelegatingMailboxListener();
        MailboxEventDispatcher mailboxEventDispatcher = new MailboxEventDispatcher(delegatingListener);
        JCRMailboxManager manager = new JCRMailboxManager(mf, noAuthenticator, noAuthorizator, locker,
            messageParser, new DefaultMessageId.Factory(), mailboxEventDispatcher, delegatingListener,
            annotationManager, storeRightManager);

        try {
            manager.init();
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }

        return manager;
    }

}
