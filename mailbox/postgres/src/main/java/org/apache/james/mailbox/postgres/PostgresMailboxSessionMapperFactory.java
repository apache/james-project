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
package org.apache.james.mailbox.postgres;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.backends.jpa.EntityManagerUtils;
import org.apache.james.backends.jpa.JPAConfiguration;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.postgres.mail.JPAAnnotationMapper;
import org.apache.james.mailbox.postgres.mail.JPAAttachmentMapper;
import org.apache.james.mailbox.postgres.mail.JPAMessageMapper;
import org.apache.james.mailbox.postgres.mail.PostgresMailboxMapper;
import org.apache.james.mailbox.postgres.mail.PostgresModSeqProvider;
import org.apache.james.mailbox.postgres.mail.PostgresUidProvider;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.postgres.user.PostgresSubscriptionDAO;
import org.apache.james.mailbox.postgres.user.PostgresSubscriptionMapper;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.user.SubscriptionMapper;

/**
 * JPA implementation of {@link MailboxSessionMapperFactory}
 *
 */
public class PostgresMailboxSessionMapperFactory extends MailboxSessionMapperFactory implements AttachmentMapperFactory {

    private final EntityManagerFactory entityManagerFactory;
    private final AttachmentMapper attachmentMapper;
    private final JPAConfiguration jpaConfiguration;
    private final PostgresExecutor.Factory executorFactory;

    @Inject
    public PostgresMailboxSessionMapperFactory(EntityManagerFactory entityManagerFactory,
                                               JPAConfiguration jpaConfiguration,
                                               PostgresExecutor.Factory executorFactory) {
        this.entityManagerFactory = entityManagerFactory;
        this.executorFactory = executorFactory;
        EntityManagerUtils.safelyClose(createEntityManager());
        this.attachmentMapper = new JPAAttachmentMapper(entityManagerFactory);
        this.jpaConfiguration = jpaConfiguration;
    }

    @Override
    public MailboxMapper createMailboxMapper(MailboxSession session) {
        PostgresMailboxDAO mailboxDAO = new PostgresMailboxDAO(executorFactory.create(session.getUser().getDomainPart()));
        return new PostgresMailboxMapper(mailboxDAO);
    }

    @Override
    public MessageMapper createMessageMapper(MailboxSession session) {
        return new JPAMessageMapper(getUidProvider(session), getModSeqProvider(session), entityManagerFactory, jpaConfiguration);
    }

    @Override
    public MessageIdMapper createMessageIdMapper(MailboxSession session) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public SubscriptionMapper createSubscriptionMapper(MailboxSession session) {
        PostgresSubscriptionDAO subscriptionDAO = new PostgresSubscriptionDAO(executorFactory.create(session.getUser().getDomainPart()));
        return new PostgresSubscriptionMapper(subscriptionDAO);
    }

    /**
     * Return a new {@link EntityManager} instance
     * 
     * @return manager
     */
    private EntityManager createEntityManager() {
        return entityManagerFactory.createEntityManager();
    }

    @Override
    public AnnotationMapper createAnnotationMapper(MailboxSession session) {
        return new JPAAnnotationMapper(entityManagerFactory);
    }

    @Override
    public UidProvider getUidProvider(MailboxSession session) {
        return new PostgresUidProvider.Factory(executorFactory).create(session);
    }

    @Override
    public ModSeqProvider getModSeqProvider(MailboxSession session) {
        return new PostgresModSeqProvider.Factory(executorFactory).create(session);
    }

    @Override
    public AttachmentMapper createAttachmentMapper(MailboxSession session) {
        return new JPAAttachmentMapper(entityManagerFactory);
    }

    @Override
    public AttachmentMapper getAttachmentMapper(MailboxSession session) {
        return attachmentMapper;
    }

}
