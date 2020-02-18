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

import org.apache.james.core.Username;
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
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.MailboxExpressionBackwardCompatibility;
import org.apache.james.mailbox.store.mail.MailboxMapper;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
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
    public MailboxId create(Mailbox mailbox) throws MailboxException {
        Preconditions.checkArgument(mailbox.getMailboxId() == null, "A mailbox we want to create should not have a mailboxId set already");

        try {
            if (isPathAlreadyUsedByAnotherMailbox(mailbox)) {
                throw new MailboxExistsException(mailbox.getName());
            }

            this.lastMailboxName = mailbox.getName();
            JPAMailbox persistedMailbox = JPAMailbox.from(mailbox);

            getEntityManager().persist(persistedMailbox);
            mailbox.setMailboxId(persistedMailbox.getMailboxId());
            return persistedMailbox.getMailboxId();
        } catch (PersistenceException e) {
            throw new MailboxException("Save of mailbox " + mailbox.getName() + " failed", e);
        }
    }
    
    @Override
    public MailboxId rename(Mailbox mailbox) throws MailboxException {
        Preconditions.checkNotNull(mailbox.getMailboxId(), "A mailbox we want to rename should have a defined mailboxId");

        try {
            begin();
            if (isPathAlreadyUsedByAnotherMailbox(mailbox)) {
                rollback();
                throw new MailboxExistsException(mailbox.getName());
            }

            this.lastMailboxName = mailbox.getName();
            JPAMailbox persistedMailbox = jpaMailbox(mailbox);

            getEntityManager().persist(persistedMailbox);
            mailbox.setMailboxId(persistedMailbox.getMailboxId());
            commit();
            return persistedMailbox.getMailboxId();
        } catch (PersistenceException e) {
            rollback();
            throw new MailboxException("Save of mailbox " + mailbox.getName() + " failed", e);
        } 
    }

    private JPAMailbox jpaMailbox(Mailbox mailbox) throws MailboxException {
        JPAMailbox result = loadJpaMailbox(mailbox.getMailboxId());
        result.setNamespace(mailbox.getNamespace());
        result.setUser(mailbox.getUser().asString());
        result.setName(mailbox.getName());
        return result;
    }

    private boolean isPathAlreadyUsedByAnotherMailbox(Mailbox mailbox) throws MailboxException {
        try {
            findMailboxByPath(mailbox.generateAssociatedPath());
            return true;
        } catch (MailboxNotFoundException e) {
            return false;
        }
    }

    @Override
    public Mailbox findMailboxByPath(MailboxPath mailboxPath) throws MailboxException, MailboxNotFoundException {
        try {
            return getEntityManager().createNamedQuery("findMailboxByNameWithUser", JPAMailbox.class)
                .setParameter("nameParam", mailboxPath.getName())
                .setParameter("namespaceParam", mailboxPath.getNamespace())
                .setParameter("userParam", mailboxPath.getUser().asString())
                .getSingleResult()
                .toMailbox();
        } catch (NoResultException e) {
            throw new MailboxNotFoundException(mailboxPath);
        } catch (PersistenceException e) {
            throw new MailboxException("Search of mailbox " + mailboxPath + " failed", e);
        }
    }

    @Override
    public Mailbox findMailboxById(MailboxId id) throws MailboxException, MailboxNotFoundException {

        try {
            return loadJpaMailbox(id).toMailbox();
        } catch (PersistenceException e) {
            throw new MailboxException("Search of mailbox " + id.serialize() + " failed", e);
        } 
    }

    private JPAMailbox loadJpaMailbox(MailboxId id) throws MailboxNotFoundException {
        JPAId mailboxId = (JPAId)id;
        try {
            return getEntityManager().createNamedQuery("findMailboxById", JPAMailbox.class)
                .setParameter("idParam", mailboxId.getRawId())
                .getSingleResult();
        } catch (NoResultException e) {
            throw new MailboxNotFoundException(mailboxId);
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
    public List<Mailbox> findMailboxWithPathLike(MailboxQuery.UserBound query) throws MailboxException {
        try {
            String pathLike = MailboxExpressionBackwardCompatibility.getPathLike(query);
            return findMailboxWithPathLikeTypedQuery(query.getFixedNamespace(), query.getFixedUser(), pathLike)
                .getResultList()
                .stream()
                .map(JPAMailbox::toMailbox)
                .filter(query::matches)
                .collect(Guavate.toImmutableList());
        } catch (PersistenceException e) {
            throw new MailboxException("Search of mailbox " + query + " failed", e);
        }
    }

    private TypedQuery<JPAMailbox> findMailboxWithPathLikeTypedQuery(String namespace, Username username, String pathLike) {
        return getEntityManager().createNamedQuery("findMailboxWithNameLikeWithUser", JPAMailbox.class)
            .setParameter("nameParam", pathLike)
            .setParameter("namespaceParam", namespace)
            .setParameter("userParam", username.asString());
    }
    
    @Override
    public boolean hasChildren(Mailbox mailbox, char delimiter) throws MailboxException, MailboxNotFoundException {
        final String name = mailbox.getName() + delimiter + SQL_WILDCARD_CHAR; 
        final Long numberOfChildMailboxes;

        numberOfChildMailboxes = (Long) getEntityManager()
            .createNamedQuery("countMailboxesWithNameLikeWithUser")
            .setParameter("nameParam", name)
            .setParameter("namespaceParam", mailbox.getNamespace())
            .setParameter("userParam", mailbox.getUser().asString())
            .getSingleResult();

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
    public List<Mailbox> findNonPersonalMailboxes(Username userName, Right right) throws MailboxException {
        return ImmutableList.of();
    }
}
