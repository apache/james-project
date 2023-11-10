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
import org.apache.james.backends.postgres.utils.JamesPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.postgres.mail.JPAAnnotationMapper;
import org.apache.james.mailbox.postgres.mail.JPAAttachmentMapper;
import org.apache.james.mailbox.postgres.mail.JPAMailboxMapper;
import org.apache.james.mailbox.postgres.mail.JPAMessageMapper;
import org.apache.james.mailbox.postgres.mail.JPAModSeqProvider;
import org.apache.james.mailbox.postgres.mail.JPAUidProvider;
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
    private final JPAUidProvider uidProvider;
    private final JPAModSeqProvider modSeqProvider;
    private final AttachmentMapper attachmentMapper;
    private final JPAConfiguration jpaConfiguration;
    private final JamesPostgresConnectionFactory postgresConnectionFactory;

    @Inject
    public PostgresMailboxSessionMapperFactory(EntityManagerFactory entityManagerFactory, JPAUidProvider uidProvider,
                                               JPAModSeqProvider modSeqProvider, JPAConfiguration jpaConfiguration,
                                               JamesPostgresConnectionFactory postgresConnectionFactory) {
        this.entityManagerFactory = entityManagerFactory;
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;
        EntityManagerUtils.safelyClose(createEntityManager());
        this.attachmentMapper = new JPAAttachmentMapper(entityManagerFactory);
        this.jpaConfiguration = jpaConfiguration;
        this.postgresConnectionFactory = postgresConnectionFactory;
    }

    @Override
    public MailboxMapper createMailboxMapper(MailboxSession session) {
        return new JPAMailboxMapper(entityManagerFactory);
    }

    @Override
    public MessageMapper createMessageMapper(MailboxSession session) {
        return new JPAMessageMapper(uidProvider, modSeqProvider, entityManagerFactory, jpaConfiguration);
    }

    @Override
    public MessageIdMapper createMessageIdMapper(MailboxSession session) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public SubscriptionMapper createSubscriptionMapper(MailboxSession session) {
        return new PostgresSubscriptionMapper(new PostgresSubscriptionDAO(new PostgresExecutor(
            postgresConnectionFactory.getConnection(session.getUser().getDomainPart()))));
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
    public UidProvider getUidProvider() {
        return uidProvider;
    }

    @Override
    public ModSeqProvider getModSeqProvider() {
        return modSeqProvider;
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
