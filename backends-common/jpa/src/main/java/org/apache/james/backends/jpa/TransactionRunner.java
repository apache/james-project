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

package org.apache.james.backends.jpa;

import java.util.function.Consumer;
import java.util.function.Function;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionRunner.class);
    public static final Function<PersistenceException, Object> IGNORE_EXCEPTION = e -> null;

    private final EntityManagerFactory entityManagerFactory;

    public TransactionRunner(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public void run(Consumer<EntityManager> runnable) {
        runAndRetrieveResult(entityManager -> {
                runnable.accept(entityManager);
                return null;
            },
            IGNORE_EXCEPTION);
    }

    public <T> T runAndRetrieveResult(Function<EntityManager, T> toResult) {
        return runAndRetrieveResult(toResult,
            e -> {
                throw new RuntimeException(e);
            });
    }

    public <T> T runAndRetrieveResult(Function<EntityManager, T> toResult,
                                      Function<PersistenceException, T> errorHandler) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            T result = toResult.apply(entityManager);
            transaction.commit();
            return result;
        } catch (PersistenceException e) {
            LOGGER.warn("Could not execute transaction", e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
            return errorHandler.apply(e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    public void runAndHandleException(Consumer<EntityManager> runnable,
                                      Consumer<PersistenceException> errorHandler) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            runnable.accept(entityManager);
            transaction.commit();
        } catch (PersistenceException e) {
            LOGGER.warn("Could not execute transaction", e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
            errorHandler.accept(e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }
}
