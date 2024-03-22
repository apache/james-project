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

import static org.apache.james.mailbox.jpa.user.model.JPASubscription.FIND_MAILBOX_SUBSCRIPTION_FOR_USER;
import static org.apache.james.mailbox.jpa.user.model.JPASubscription.FIND_SUBSCRIPTIONS_FOR_USER;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceException;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.jpa.JPATransactionalMapper;
import org.apache.james.mailbox.jpa.user.model.JPASubscription;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.model.Subscription;

import com.google.common.collect.ImmutableList;

/**
 * JPA implementation of a {@link SubscriptionMapper}. This class is not thread-safe!
 */
public class JPASubscriptionMapper extends JPATransactionalMapper implements SubscriptionMapper {

    public JPASubscriptionMapper(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    public void save(Subscription subscription) throws SubscriptionException {
        EntityManager entityManager = getEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        boolean localTransaction = !transaction.isActive();
        if (localTransaction) {
            transaction.begin();
        }
        try {
            if (!exists(entityManager, subscription)) {
                entityManager.persist(new JPASubscription(subscription));
            }
            if (localTransaction) {
                if (transaction.isActive()) {
                    transaction.commit();
                }
            }
        } catch (PersistenceException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new SubscriptionException(e);
        }
    }

    @Override
    public List<Subscription> findSubscriptionsForUser(Username user) throws SubscriptionException {
        try {
            return getEntityManager().createNamedQuery(FIND_SUBSCRIPTIONS_FOR_USER, JPASubscription.class)
                .setParameter("userParam", user.asString())
                .getResultList()
                .stream()
                .map(JPASubscription::toSubscription)
                .collect(ImmutableList.toImmutableList());
        } catch (PersistenceException e) {
            throw new SubscriptionException(e);
        }
    }

    @Override
    public void delete(Subscription subscription) throws SubscriptionException {
        EntityManager entityManager = getEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        boolean localTransaction = !transaction.isActive();
        if (localTransaction) {
            transaction.begin();
        }
        try {
            findJpaSubscription(entityManager, subscription)
                .ifPresent(entityManager::remove);
            if (localTransaction) {
                if (transaction.isActive()) {
                    transaction.commit();
                }
            }
        } catch (PersistenceException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new SubscriptionException(e);
        }
    }

    private Optional<JPASubscription> findJpaSubscription(EntityManager entityManager, Subscription subscription) {
        return entityManager.createNamedQuery(FIND_MAILBOX_SUBSCRIPTION_FOR_USER, JPASubscription.class)
            .setParameter("userParam", subscription.getUser().asString())
            .setParameter("mailboxParam", subscription.getMailbox())
            .getResultList()
            .stream()
            .findFirst();
    }

    private boolean exists(EntityManager entityManager, Subscription subscription) throws SubscriptionException {
        try {
            return !entityManager.createNamedQuery(FIND_MAILBOX_SUBSCRIPTION_FOR_USER, JPASubscription.class)
                .setParameter("userParam", subscription.getUser().asString())
                .setParameter("mailboxParam", subscription.getMailbox())
                .getResultList().isEmpty();
        } catch (NoResultException e) {
            return false;
        } catch (PersistenceException e) {
            throw new SubscriptionException(e);
        }
    }
}
