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
import org.apache.james.mailbox.store.mail.AbstractLockingUidProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;

public class JPAUidProvider extends AbstractLockingUidProvider<JPAId> {

    private EntityManagerFactory factory;

    public JPAUidProvider(MailboxPathLocker locker, EntityManagerFactory factory) {
        super(locker);
        this.factory = factory;
    }
    
    
    @Override
    public long lastUid(MailboxSession session, Mailbox<JPAId> mailbox) throws MailboxException {
        EntityManager manager = null;
        try {
            manager = factory.createEntityManager();
            manager.getTransaction().begin();
            long uid = (Long) manager.createNamedQuery("findLastUid").setParameter("idParam", mailbox.getMailboxId().getRawId()).getSingleResult();
            manager.getTransaction().commit();
            return uid;
        } catch (PersistenceException e) {
            if (manager != null && manager.getTransaction().isActive()) {
                manager.getTransaction().rollback();
            }
            throw new MailboxException("Unable to get last uid for mailbox " + mailbox, e);
        } finally {
            if (manager != null) {
                manager.close();
            }
        }
    }

    @Override
    protected long lockedNextUid(MailboxSession session, Mailbox<JPAId> mailbox) throws MailboxException {
        EntityManager manager = null;
        try {
            manager = factory.createEntityManager();
            manager.getTransaction().begin();
            JPAMailbox m = manager.find(JPAMailbox.class, mailbox.getMailboxId().getRawId());
            long uid = m.consumeUid();
            manager.persist(m);
            manager.getTransaction().commit();
            return uid;
        } catch (PersistenceException e) {
            if (manager != null && manager.getTransaction().isActive()) {
                manager.getTransaction().rollback();
            }
            throw new MailboxException("Unable to save next uid for mailbox " + mailbox, e);
        } finally {
            if (manager != null) {
                manager.close();
            }
        }
    }

}
