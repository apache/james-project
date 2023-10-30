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
package org.apache.james.mailbox.jpa.mail;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;

import org.apache.james.backends.jpa.EntityManagerUtils;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.ModSeqProvider;

public class JPAModSeqProvider implements ModSeqProvider {

    private final EntityManagerFactory factory;

    @Inject
    public JPAModSeqProvider(EntityManagerFactory factory) {
        this.factory = factory;
    }

    @Override
    public ModSeq highestModSeq(Mailbox mailbox) throws MailboxException {
        JPAId mailboxId = (JPAId) mailbox.getMailboxId();
        return highestModSeq(mailboxId);
    }

    @Override
    public ModSeq nextModSeq(Mailbox mailbox) throws MailboxException {
        return nextModSeq((JPAId) mailbox.getMailboxId());
    }

    @Override
    public ModSeq nextModSeq(MailboxId mailboxId) throws MailboxException {
        return nextModSeq((JPAId) mailboxId);
    }

    @Override
    public ModSeq highestModSeq(MailboxId mailboxId) throws MailboxException {
        return highestModSeq((JPAId) mailboxId);
    }

    private ModSeq nextModSeq(JPAId mailboxId) throws MailboxException {
        EntityManager manager = null;
        try {
            manager = factory.createEntityManager();
            manager.getTransaction().begin();
            JPAMailbox m = manager.find(JPAMailbox.class, mailboxId.getRawId());
            long modSeq = m.consumeModSeq();
            manager.persist(m);
            manager.getTransaction().commit();
            return ModSeq.of(modSeq);
        } catch (PersistenceException e) {
            if (manager != null && manager.getTransaction().isActive()) {
                manager.getTransaction().rollback();
            }
            throw new MailboxException("Unable to save highest mod-sequence for mailbox " + mailboxId.serialize(), e);
        } finally {
            EntityManagerUtils.safelyClose(manager);
        }
    }

    private ModSeq highestModSeq(JPAId mailboxId) throws MailboxException {
        EntityManager manager = factory.createEntityManager();
        try {
            return highestModSeq(mailboxId, manager);
        } finally {
            EntityManagerUtils.safelyClose(manager);
        }
    }

    public ModSeq highestModSeq(MailboxId mailboxId, EntityManager manager) throws MailboxException {
        JPAId jpaId = (JPAId) mailboxId;
        try {
            long highest = (Long) manager.createNamedQuery("findHighestModSeq")
                .setParameter("idParam", jpaId.getRawId())
                .getSingleResult();
            return ModSeq.of(highest);
        } catch (PersistenceException e) {
            throw new MailboxException("Unable to get highest mod-sequence for mailbox " + mailboxId.serialize(), e);
        }
    }
}
