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

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.JPATransactionalMapper;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;

/**
 * Data access management for mailbox.
 */
public class JPAMailboxMapper extends JPATransactionalMapper implements MailboxMapper<JPAId> {

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
            if (e instanceof EntityExistsException)
                throw new MailboxExistsException(lastMailboxName);
            if (e instanceof RollbackException) {
                Throwable t = e.getCause();
                if (t != null && t instanceof EntityExistsException)
                    throw new MailboxExistsException(lastMailboxName);
            }
            throw new MailboxException("Commit of transaction failed", e);
        }
    }
    
    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#save(Mailbox)
     */
    public void save(Mailbox<JPAId> mailbox) throws MailboxException {
        try {
            this.lastMailboxName = mailbox.getName();
            getEntityManager().persist(mailbox);
        } catch (PersistenceException e) {
            throw new MailboxException("Save of mailbox " + mailbox.getName() +" failed", e);
        } 
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#findMailboxByPath(MailboxPath)
     */
    public Mailbox<JPAId> findMailboxByPath(MailboxPath mailboxPath) throws MailboxException, MailboxNotFoundException {
        try {
            if (mailboxPath.getUser() == null) {
                return (JPAMailbox) getEntityManager().createNamedQuery("findMailboxByName").setParameter("nameParam", mailboxPath.getName()).setParameter("namespaceParam", mailboxPath.getNamespace()).getSingleResult();
            } else {
                return (JPAMailbox) getEntityManager().createNamedQuery("findMailboxByNameWithUser").setParameter("nameParam", mailboxPath.getName()).setParameter("namespaceParam", mailboxPath.getNamespace()).setParameter("userParam", mailboxPath.getUser()).getSingleResult();
            }
        } catch (NoResultException e) {
            throw new MailboxNotFoundException(mailboxPath);
        } catch (PersistenceException e) {
            throw new MailboxException("Search of mailbox " + mailboxPath + " failed", e);
        } 
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#delete(Mailbox)
     */
    public void delete(Mailbox<JPAId> mailbox) throws MailboxException {
        try {  
            getEntityManager().createNamedQuery("deleteMessages").setParameter("idParam", mailbox.getMailboxId().getRawId()).executeUpdate();
            getEntityManager().remove(mailbox);
        } catch (PersistenceException e) {
            throw new MailboxException("Delete of mailbox " + mailbox + " failed", e);
        } 
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#findMailboxWithPathLike(MailboxPath)
     */
    @SuppressWarnings("unchecked")
    public List<Mailbox<JPAId>> findMailboxWithPathLike(MailboxPath path) throws MailboxException {
        try {
            if (path.getUser() == null) {
                return getEntityManager().createNamedQuery("findMailboxWithNameLike").setParameter("nameParam", SQL_WILDCARD_CHAR + path.getName() + SQL_WILDCARD_CHAR).setParameter("namespaceParam", path.getNamespace()).getResultList();
            } else {
                return getEntityManager().createNamedQuery("findMailboxWithNameLikeWithUser").setParameter("nameParam", SQL_WILDCARD_CHAR + path.getName() + SQL_WILDCARD_CHAR).setParameter("namespaceParam", path.getNamespace()).setParameter("userParam", path.getUser()).getResultList();
            }
        } catch (PersistenceException e) {
            throw new MailboxException("Search of mailbox " + path + " failed", e);
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
    
    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#hasChildren(Mailbox, char)
     */
    public boolean hasChildren(Mailbox<JPAId> mailbox, char delimiter) throws MailboxException,
            MailboxNotFoundException {
        final String name = mailbox.getName() + delimiter + SQL_WILDCARD_CHAR; 
        final Long numberOfChildMailboxes;
        if (mailbox.getUser() == null) {
            numberOfChildMailboxes = (Long) getEntityManager().createNamedQuery("countMailboxesWithNameLike").setParameter("nameParam", name).setParameter("namespaceParam", mailbox.getNamespace()).getSingleResult();
        } else {
            numberOfChildMailboxes = (Long) getEntityManager().createNamedQuery("countMailboxesWithNameLikeWithUser").setParameter("nameParam", name).setParameter("namespaceParam", mailbox.getNamespace()).setParameter("userParam", mailbox.getUser()).getSingleResult();
        }
        return numberOfChildMailboxes != null && numberOfChildMailboxes > 0;
    }

	/**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#list()
     */
    @SuppressWarnings("unchecked")
    public List<Mailbox<JPAId>> list() throws MailboxException{
        try {
            return getEntityManager().createNamedQuery("listMailboxes").getResultList();
        } catch (PersistenceException e) {
            throw new MailboxException("Delete of mailboxes failed", e);
        } 
    }

    @Override
    public void updateACL(Mailbox<JPAId> mailbox, MailboxACL.MailboxACLCommand mailboxACLCommand) throws MailboxException {
        mailbox.setACL(mailbox.getACL().apply(mailboxACLCommand));
    }
}
