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

import java.util.List;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.RollbackException;
import javax.persistence.TypedQuery;

import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.JPATransactionalMapper;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

/**
 * Data access management for mailbox.
 */
public class JPAMailboxMapper extends JPATransactionalMapper implements MailboxMapper {

    private static final char SQL_WILDCARD_CHAR = '%';
    private String lastMailboxName;
    
    public JPAMailboxMapper(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    /**
     * Commit the transaction. If the commit fails due a conflict in a unique key constraint a {@link MailboxExistsException}
     * will get thrown
     */
    @Override
    protected void commit() throws MailboxException {
        try {
            getEntityManager().getTransaction().commit();
        } catch (PersistenceException e) {
            if (e instanceof EntityExistsException) {
                throw new MailboxExistsException(lastMailboxName);
            }
            if (e instanceof RollbackException) {
                Throwable t = e.getCause();
                if (t != null && t instanceof EntityExistsException) {
                    throw new MailboxExistsException(lastMailboxName);
                }
            }
            throw new MailboxException("Commit of transaction failed", e);
        }
    }
    
    @Override
    public MailboxId save(Mailbox mailbox) throws MailboxException {
        try {
            if (isPathAlreadyUsedByAnotherMailbox(mailbox)) {
                throw new MailboxExistsException(mailbox.getName());
            }

            this.lastMailboxName = mailbox.getName();
            JPAMailbox persistedMailbox = JPAMailbox.from(mailbox);

            getEntityManager().persist(persistedMailbox);
            mailbox.setMailboxId(persistedMailbox.getMailboxId());
            return mailbox.getMailboxId();
        } catch (PersistenceException e) {
            throw new MailboxException("Save of mailbox " + mailbox.getName() + " failed", e);
        } 
    }

    private boolean isPathAlreadyUsedByAnotherMailbox(Mailbox mailbox) throws MailboxException {
        try {
            Mailbox storedMailbox = findMailboxByPath(mailbox.generateAssociatedPath());
            return !Objects.equal(storedMailbox.getMailboxId(), mailbox.getMailboxId());
        } catch (MailboxNotFoundException e) {
            return false;
        }
    }

    @Override
    public Mailbox findMailboxByPath(MailboxPath mailboxPath) throws MailboxException, MailboxNotFoundException {
        try {
            if (mailboxPath.getUser() == null) {
                return getEntityManager().createNamedQuery("findMailboxByName", JPAMailbox.class)
                    .setParameter("nameParam", mailboxPath.getName())
                    .setParameter("namespaceParam", mailboxPath.getNamespace())
                    .getSingleResult()
                    .toMailbox();
            } else {
                return getEntityManager().createNamedQuery("findMailboxByNameWithUser", JPAMailbox.class)
                    .setParameter("nameParam", mailboxPath.getName())
                    .setParameter("namespaceParam", mailboxPath.getNamespace())
                    .setParameter("userParam", mailboxPath.getUser())
                    .getSingleResult()
                    .toMailbox();
            }
        } catch (NoResultException e) {
            throw new MailboxNotFoundException(mailboxPath);
        } catch (PersistenceException e) {
            throw new MailboxException("Search of mailbox " + mailboxPath + " failed", e);
        }
    }

    @Override
    public Mailbox findMailboxById(MailboxId id) throws MailboxException, MailboxNotFoundException {
        JPAId mailboxId = (JPAId)id;
        try {
            return getEntityManager().createNamedQuery("findMailboxById", JPAMailbox.class)
                .setParameter("idParam", mailboxId.getRawId())
                .getSingleResult()
                .toMailbox();
        } catch (NoResultException e) {
            throw new MailboxNotFoundException(mailboxId);
        } catch (PersistenceException e) {
            throw new MailboxException("Search of mailbox " + mailboxId.serialize() + " failed", e);
        } 
    }

    @Override
    public void delete(Mailbox mailbox) throws MailboxException {
        try {  
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();
            getEntityManager().createNamedQuery("deleteMessages").setParameter("idParam", mailboxId.getRawId()).executeUpdate();
            JPAMailbox jpaMailbox = getEntityManager().find(JPAMailbox.class, mailboxId.getRawId());
            getEntityManager().remove(jpaMailbox);
        } catch (PersistenceException e) {
            throw new MailboxException("Delete of mailbox " + mailbox + " failed", e);
        } 
    }

    @Override
    public List<Mailbox> findMailboxWithPathLike(MailboxPath path) throws MailboxException {
        try {
            return findMailboxWithPathLikeTypedQuery(path)
                .getResultList()
                .stream()
                .map(JPAMailbox::toMailbox)
                .collect(Guavate.toImmutableList());
        } catch (PersistenceException e) {
            throw new MailboxException("Search of mailbox " + path + " failed", e);
        }
    }

    private TypedQuery<JPAMailbox> findMailboxWithPathLikeTypedQuery(MailboxPath path) {
        if (path.getUser() == null) {
            return getEntityManager().createNamedQuery("findMailboxWithNameLike", JPAMailbox.class)
                .setParameter("nameParam", path.getName())
                .setParameter("namespaceParam", path.getNamespace());
        } else {
            return getEntityManager().createNamedQuery("findMailboxWithNameLikeWithUser", JPAMailbox.class)
                .setParameter("nameParam", path.getName())
                .setParameter("namespaceParam", path.getNamespace())
                .setParameter("userParam", path.getUser());
        }
    }

    public void deleteAllMemberships() throws MailboxException {
        try {
            getEntityManager().createNamedQuery("deleteAllMemberships").executeUpdate();
        } catch (PersistenceException e) {
            throw new MailboxException("Delete of mailboxes failed", e);
        } 
    }
    
    public void deleteAllMailboxes() throws MailboxException {
        try {
            getEntityManager().createNamedQuery("deleteAllMailboxes").executeUpdate();
        } catch (PersistenceException e) {
            throw new MailboxException("Delete of mailboxes failed", e);
        } 
    }
    
    @Override
    public boolean hasChildren(Mailbox mailbox, char delimiter) throws MailboxException, MailboxNotFoundException {
        final String name = mailbox.getName() + delimiter + SQL_WILDCARD_CHAR; 
        final Long numberOfChildMailboxes;
        if (mailbox.getUser() == null) {
            numberOfChildMailboxes = (Long) getEntityManager().createNamedQuery("countMailboxesWithNameLike").setParameter("nameParam", name).setParameter("namespaceParam", mailbox.getNamespace()).getSingleResult();
        } else {
            numberOfChildMailboxes = (Long) getEntityManager().createNamedQuery("countMailboxesWithNameLikeWithUser").setParameter("nameParam", name).setParameter("namespaceParam", mailbox.getNamespace()).setParameter("userParam", mailbox.getUser()).getSingleResult();
        }
        return numberOfChildMailboxes != null && numberOfChildMailboxes > 0;
    }

    @Override
    public List<Mailbox> list() throws MailboxException {
        try {
            return getEntityManager().createNamedQuery("listMailboxes", JPAMailbox.class).getResultList()
                .stream()
                .map(JPAMailbox::toMailbox)
                .collect(Guavate.toImmutableList());
        } catch (PersistenceException e) {
            throw new MailboxException("Delete of mailboxes failed", e);
        } 
    }

    @Override
    public ACLDiff updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand) throws MailboxException {
        MailboxACL oldACL = mailbox.getACL();
        MailboxACL newACL = mailbox.getACL().apply(mailboxACLCommand);
        mailbox.setACL(newACL);
        return ACLDiff.computeDiff(oldACL, newACL);
    }

    @Override
    public ACLDiff setACL(Mailbox mailbox, MailboxACL mailboxACL) throws MailboxException {
        MailboxACL oldMailboxAcl = mailbox.getACL();
        mailbox.setACL(mailboxACL);
        return ACLDiff.computeDiff(oldMailboxAcl, mailboxACL);
    }

    @Override
    public List<Mailbox> findNonPersonalMailboxes(String userName, Right right) throws MailboxException {
        return ImmutableList.of();
    }
}
