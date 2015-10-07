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
package org.apache.james.mailbox.jpa.user;

import java.util.List;

import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;

import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.jpa.JPATransactionalMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.model.Subscription;

/**
 * JPA implementation of a {@link SubscriptionMapper}. This class is not thread-safe!
 */
public class JPASubscriptionMapper extends JPATransactionalMapper implements SubscriptionMapper {

    public JPASubscriptionMapper(final EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    
    /**
     * @see org.apache.james.mailbox.store.user.SubscriptionMapper#findMailboxSubscriptionForUser(java.lang.String, java.lang.String)
     */
    public Subscription findMailboxSubscriptionForUser(final String user, final String mailbox) throws SubscriptionException {
        try {
            return (Subscription) getEntityManager().createNamedQuery("findFindMailboxSubscriptionForUser")
            .setParameter("userParam", user).setParameter("mailboxParam", mailbox).getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (PersistenceException e) {
            throw new SubscriptionException(e);
        }
    }

    /**
     * @throws SubscriptionException 
     * @see org.apache.james.mailbox.store.user.SubscriptionMapper#save(Subscription)
     */
    public void save(Subscription subscription) throws SubscriptionException {
        try {
            getEntityManager().persist(subscription);
        } catch (PersistenceException e) {
            throw new SubscriptionException(e);
        }
    }

    /**
     * @throws SubscriptionException 
     * @see org.apache.james.mailbox.store.user.SubscriptionMapper#findSubscriptionsForUser(java.lang.String)
     */
    @SuppressWarnings("unchecked")
    public List<Subscription> findSubscriptionsForUser(String user) throws SubscriptionException {
        try {
            return (List<Subscription>) getEntityManager().createNamedQuery("findSubscriptionsForUser").setParameter("userParam", user).getResultList();
        } catch (PersistenceException e) {
            throw new SubscriptionException(e);
        }
    }

    /**
     * @throws SubscriptionException 
     * @see org.apache.james.mailbox.store.user.SubscriptionMapper#delete(Subscription)
     */
    public void delete(Subscription subscription) throws SubscriptionException {
        try {
            getEntityManager().remove(subscription);
        } catch (PersistenceException e) {
            throw new SubscriptionException(e);
        }
    }
}
