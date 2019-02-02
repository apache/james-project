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

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.mail.JPAAnnotationMapper;
import org.apache.james.mailbox.jpa.mail.JPAMailboxMapper;
import org.apache.james.mailbox.jpa.mail.JPAMessageMapper;
import org.apache.james.mailbox.jpa.user.JPASubscriptionMapper;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
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
public class JPAMailboxSessionMapperFactory extends MailboxSessionMapperFactory {

    private final EntityManagerFactory entityManagerFactory;
    private final UidProvider uidProvider;
    private final ModSeqProvider modSeqProvider;

    @Inject
    public JPAMailboxSessionMapperFactory(EntityManagerFactory entityManagerFactory, UidProvider uidProvider, ModSeqProvider modSeqProvider) {
        this.entityManagerFactory = entityManagerFactory;
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;
        createEntityManager().close();   
    }
    
    @Override
    public MailboxMapper createMailboxMapper(MailboxSession session) {
        return new JPAMailboxMapper(entityManagerFactory);
    }

    @Override
    public MessageMapper createMessageMapper(MailboxSession session) {
        return new JPAMessageMapper(session, uidProvider, modSeqProvider, entityManagerFactory);
    }

    @Override
    public MessageIdMapper createMessageIdMapper(MailboxSession session) throws MailboxException {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public SubscriptionMapper createSubscriptionMapper(MailboxSession session) {
        return new JPASubscriptionMapper(entityManagerFactory);
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
    public AnnotationMapper createAnnotationMapper(MailboxSession session)
            throws MailboxException {
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

}
