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

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.store.mail.AbstractLockingModSeqProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;

public class JPAModSeqProvider extends AbstractLockingModSeqProvider<JPAId> {

    private EntityManagerFactory factory;

    public JPAModSeqProvider(MailboxPathLocker locker, EntityManagerFactory factory) {
        super(locker);
        this.factory = factory;
    }

    @Override
    public long highestModSeq(MailboxSession session, Mailbox<JPAId> mailbox) throws MailboxException {
        EntityManager manager = null;
        try {
            manager = factory.createEntityManager();
            manager.getTransaction().begin();
            long highest = (Long) manager.createNamedQuery("findHighestModSeq").setParameter("idParam", mailbox.getMailboxId().getRawId()).getSingleResult();
            manager.getTransaction().commit();
            return highest;
        } catch (PersistenceException e) {
            if (manager != null && manager.getTransaction().isActive()) {
                manager.getTransaction().rollback();
            }
            throw new MailboxException("Unable to get highest mod-sequence for mailbox " + mailbox, e);
        } finally {
            if (manager != null) {
                manager.close();
            }
        }
    }

    @Override
    protected long lockedNextModSeq(MailboxSession session, Mailbox<JPAId> mailbox) throws MailboxException {
        EntityManager manager = null;
        try {
            manager = factory.createEntityManager();
            manager.getTransaction().begin();
            JPAMailbox m = manager.find(JPAMailbox.class, mailbox.getMailboxId().getRawId());
            long modSeq = m.consumeModSeq();
            manager.persist(m);
            manager.getTransaction().commit();
            return modSeq;
        } catch (PersistenceException e) {
            if (manager != null && manager.getTransaction().isActive()) {
                manager.getTransaction().rollback();
            }
            throw new MailboxException("Unable to save highest mod-sequence for mailbox " + mailbox, e);
        } finally {
            if (manager != null) {
                manager.close();
            }
        }
    }

}
