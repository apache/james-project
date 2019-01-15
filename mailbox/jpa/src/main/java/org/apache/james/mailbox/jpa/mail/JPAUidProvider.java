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

import java.util.Optional;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.store.mail.AbstractLockingUidProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;

public class JPAUidProvider extends AbstractLockingUidProvider {

    private final EntityManagerFactory factory;
    private JPAMailbox jpaMailbox;

    @Inject
    public JPAUidProvider(MailboxPathLocker locker, EntityManagerFactory factory) {
        super(locker);
        this.factory = factory;
    }
    
    
    @Override
    public Optional<MessageUid> lastUid(MailboxSession session, Mailbox mailbox) throws MailboxException {
        EntityManager manager = null;
        try {
            manager = factory.createEntityManager();
            manager.getTransaction().begin();
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();
            long uid = (Long) manager.createNamedQuery("findLastUid").setParameter("idParam", mailboxId.getRawId()).getSingleResult();
            manager.getTransaction().commit();
            if (uid == 0) {
                return Optional.empty();
            }
            return Optional.of(MessageUid.of(uid));
        } catch (PersistenceException e) {
            if (manager != null && manager.getTransaction().isActive()) {
                manager.getTransaction().rollback();
            }
            throw new MailboxException("Unable to get last uid for mailbox " + mailbox, e);
        } finally {
            if (manager != null && !manager.getTransaction().isActive()) {
                manager.close();
            }
        }
    }

    @Override
    protected MessageUid lockedNextUid(MailboxSession session, Mailbox mailbox) throws MailboxException {
        EntityManager manager = null;
        try {
            manager = factory.createEntityManager();
            manager.getTransaction().begin();
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();
            JPAMailbox m = Optional.ofNullable(manager.find(JPAMailbox.class, mailboxId.getRawId()))
                    .orElse(JPAMailbox.from(mailbox));
            JPAMailbox mAftererge = manager.merge(m);
            long uid = mAftererge.consumeUid();
            mailbox.setMailboxId(mAftererge.getMailboxId());
            manager.getTransaction().commit();
            return MessageUid.of(uid);
        } catch (PersistenceException e) {
            if (manager != null && manager.getTransaction().isActive()) {
                manager.getTransaction().rollback();
            }
                throw new MailboxException("Unable to save next uid for mailbox " + mailbox, e);
        }
        finally {
            if (manager != null && !manager.getTransaction().isActive()) {
                manager.close();
            }
        }
    }

}
