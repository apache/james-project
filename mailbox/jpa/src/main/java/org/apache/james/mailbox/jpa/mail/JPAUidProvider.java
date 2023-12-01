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

import javax.inject.Inject;

import org.apache.james.backends.jpa.EntityManagerUtils;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.UidProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;

public class JPAUidProvider implements UidProvider {

    private final EntityManagerFactory factory;

    @Inject
    public JPAUidProvider(EntityManagerFactory factory) {
        this.factory = factory;
    }

    @Override
    public Optional<MessageUid> lastUid(Mailbox mailbox) throws MailboxException {
        EntityManager manager = factory.createEntityManager();
        try {
            return lastUid(mailbox, manager);
        } finally {
            EntityManagerUtils.safelyClose(manager);
        }
    }

    public Optional<MessageUid> lastUid(Mailbox mailbox, EntityManager manager) throws MailboxException {
        try {
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();
            long uid = (Long) manager.createNamedQuery("findLastUid").setParameter("idParam", mailboxId.getRawId()).getSingleResult();
            if (uid == 0) {
                return Optional.empty();
            }
            return Optional.of(MessageUid.of(uid));
        } catch (PersistenceException e) {
            throw new MailboxException("Unable to get last uid for mailbox " + mailbox, e);
        }
    }

    @Override
    public MessageUid nextUid(Mailbox mailbox) throws MailboxException {
        return nextUid((JPAId) mailbox.getMailboxId());
    }

    @Override
    public MessageUid nextUid(MailboxId mailboxId) throws MailboxException {
        return nextUid((JPAId) mailboxId);
    }

    private MessageUid nextUid(JPAId mailboxId) throws MailboxException {
        EntityManager manager = null;
        try {
            manager = factory.createEntityManager();
            manager.getTransaction().begin();
            JPAMailbox m = manager.find(JPAMailbox.class, mailboxId.getRawId());
            long uid = m.consumeUid();
            manager.persist(m);
            manager.getTransaction().commit();
            return MessageUid.of(uid);
        } catch (PersistenceException e) {
            if (manager != null && manager.getTransaction().isActive()) {
                manager.getTransaction().rollback();
            }
            throw new MailboxException("Unable to save next uid for mailbox " + mailboxId.serialize(), e);
        } finally {
            EntityManagerUtils.safelyClose(manager);
        }
    }

}
