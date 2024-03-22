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

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceException;

import org.apache.james.backends.jpa.EntityManagerUtils;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.transaction.TransactionalMapper;

/**
 * JPA implementation of TransactionMapper. This class is not thread-safe!
 *
 */
public abstract class JPATransactionalMapper extends TransactionalMapper {

    protected EntityManagerFactory entityManagerFactory;
    protected EntityManager entityManager;
    
    public JPATransactionalMapper(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    /**
     * Return the currently used {@link EntityManager} or a new one if none exists.
     * 
     * @return entitymanger
     */
    public EntityManager getEntityManager() {
        if (entityManager != null) {
            return entityManager;
        }
        entityManager = entityManagerFactory.createEntityManager();
        return entityManager;
    }

    @Override
    protected void begin() throws MailboxException {
        try {
            getEntityManager().getTransaction().begin();
        } catch (PersistenceException e) {
            throw new MailboxException("Begin of transaction failed", e);
        }
    }

    /**
     * Commit the Transaction and close the EntityManager
     */
    @Override
    protected void commit() throws MailboxException {
        try {
            getEntityManager().getTransaction().commit();
        } catch (PersistenceException e) {
            throw new MailboxException("Commit of transaction failed",e);
        }
    }

    @Override
    protected void rollback() throws MailboxException {
        EntityTransaction transaction = entityManager.getTransaction();
        // check if we have a transaction to rollback
        if (transaction.isActive()) {
            getEntityManager().getTransaction().rollback();
        }
    }

    /**
     * Close open {@link EntityManager}
     */
    @Override
    public void endRequest() {
        EntityManagerUtils.safelyClose(entityManager);
        entityManager = null;
    }

    
}
