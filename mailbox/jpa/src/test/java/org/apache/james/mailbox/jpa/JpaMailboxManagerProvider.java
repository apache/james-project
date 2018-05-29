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

package org.apache.james.mailbox.jpa;

import javax.persistence.EntityManagerFactory;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.mail.JPAModSeqProvider;
import org.apache.james.mailbox.jpa.mail.JPAUidProvider;
import org.apache.james.mailbox.jpa.openjpa.OpenJPAMailboxManager;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;

public class JpaMailboxManagerProvider {

    private static final int LIMIT_ANNOTATIONS = 3;
    private static final int LIMIT_ANNOTATION_SIZE = 30;

    public static OpenJPAMailboxManager provideMailboxManager(JpaTestCluster jpaTestCluster) {
        EntityManagerFactory entityManagerFactory = jpaTestCluster.getEntityManagerFactory();
        JVMMailboxPathLocker locker = new JVMMailboxPathLocker();
        JPAMailboxSessionMapperFactory mf = new JPAMailboxSessionMapperFactory(entityManagerFactory, new JPAUidProvider(locker, entityManagerFactory), new JPAModSeqProvider(locker, entityManagerFactory));

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();

        Authenticator noAuthenticator = null;
        Authorizator noAuthorizator = null;
        DefaultDelegatingMailboxListener delegatingListener = new DefaultDelegatingMailboxListener();
        MailboxEventDispatcher mailboxEventDispatcher = new MailboxEventDispatcher(delegatingListener);
        StoreRightManager storeRightManager = new StoreRightManager(mf, aclResolver, groupMembershipResolver, mailboxEventDispatcher);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mf, storeRightManager,
            LIMIT_ANNOTATIONS, LIMIT_ANNOTATION_SIZE);
        OpenJPAMailboxManager openJPAMailboxManager = new OpenJPAMailboxManager(mf, noAuthenticator, noAuthorizator,
            messageParser, new DefaultMessageId.Factory(),
            delegatingListener, mailboxEventDispatcher, annotationManager,
            storeRightManager);

        try {
            openJPAMailboxManager.init();
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }

        return openJPAMailboxManager;
    }
}
