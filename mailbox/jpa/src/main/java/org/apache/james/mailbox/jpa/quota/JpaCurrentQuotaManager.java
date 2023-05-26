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

package org.apache.james.mailbox.jpa.quota;

import java.util.Optional;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.james.backends.jpa.EntityManagerUtils;
import org.apache.james.backends.jpa.TransactionRunner;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.jpa.quota.model.JpaCurrentQuota;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;

import reactor.core.publisher.Mono;

public class JpaCurrentQuotaManager implements CurrentQuotaManager {

    public static final long NO_MESSAGES = 0L;
    public static final long NO_STORED_BYTES = 0L;

    private final EntityManagerFactory entityManagerFactory;
    private final TransactionRunner transactionRunner;

    @Inject
    public JpaCurrentQuotaManager(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
        this.transactionRunner = new TransactionRunner(entityManagerFactory);
    }

    @Override
    public Mono<QuotaCountUsage> getCurrentMessageCount(QuotaRoot quotaRoot) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        try {
            return Mono.fromCallable(() -> Optional.ofNullable(retrieveUserQuota(entityManager, quotaRoot))
                .map(JpaCurrentQuota::getMessageCount)
                .orElse(QuotaCountUsage.count(NO_STORED_BYTES)));
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    @Override
    public Mono<QuotaSizeUsage> getCurrentStorage(QuotaRoot quotaRoot) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        try {
            return Mono.fromCallable(() -> Optional.ofNullable(retrieveUserQuota(entityManager, quotaRoot))
                .map(JpaCurrentQuota::getSize)
                .orElse(QuotaSizeUsage.size(NO_STORED_BYTES)));
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    public Mono<CurrentQuotas> getCurrentQuotas(QuotaRoot quotaRoot) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            return Mono.fromCallable(() ->  Optional.ofNullable(retrieveUserQuota(entityManager, quotaRoot))
                .map(jpaCurrentQuota -> new CurrentQuotas(jpaCurrentQuota.getMessageCount(), jpaCurrentQuota.getSize()))
                .orElse(CurrentQuotas.emptyQuotas()));
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    @Override
    public Mono<Void> increase(QuotaOperation quotaOperation) {
        return Mono.fromRunnable(() ->
            transactionRunner.run(
                entityManager -> {
                    QuotaRoot quotaRoot = quotaOperation.quotaRoot();

                    JpaCurrentQuota jpaCurrentQuota = Optional.ofNullable(retrieveUserQuota(entityManager, quotaRoot))
                        .orElse(new JpaCurrentQuota(quotaRoot.getValue(), NO_MESSAGES, NO_STORED_BYTES));

                    entityManager.merge(new JpaCurrentQuota(quotaRoot.getValue(),
                        jpaCurrentQuota.getMessageCount().asLong() + quotaOperation.count().asLong(),
                        jpaCurrentQuota.getSize().asLong() + quotaOperation.size().asLong()));
                }));
    }

    @Override
    public Mono<Void> decrease(QuotaOperation quotaOperation) {
        return Mono.fromRunnable(() ->
            transactionRunner.run(
                entityManager -> {
                    QuotaRoot quotaRoot = quotaOperation.quotaRoot();

                    JpaCurrentQuota jpaCurrentQuota = Optional.ofNullable(retrieveUserQuota(entityManager, quotaRoot))
                        .orElse(new JpaCurrentQuota(quotaRoot.getValue(), NO_MESSAGES, NO_STORED_BYTES));

                    entityManager.merge(new JpaCurrentQuota(quotaRoot.getValue(),
                        jpaCurrentQuota.getMessageCount().asLong() - quotaOperation.count().asLong(),
                        jpaCurrentQuota.getSize().asLong() - quotaOperation.size().asLong()));
                }));
    }

    @Override
    public Mono<Void> setCurrentQuotas(QuotaOperation quotaOperation) {
        return Mono.fromCallable(() -> getCurrentQuotas(quotaOperation.quotaRoot()))
            .flatMap(storedQuotas -> Mono.fromRunnable(() ->
                transactionRunner.run(
                    entityManager -> {
                        if (!storedQuotas.equals(CurrentQuotas.from(quotaOperation))) {
                            entityManager.merge(new JpaCurrentQuota(quotaOperation.quotaRoot().getValue(),
                                quotaOperation.count().asLong(),
                                quotaOperation.size().asLong()));
                        }
                    })));
    }

    private JpaCurrentQuota retrieveUserQuota(EntityManager entityManager, QuotaRoot quotaRoot) {
        return entityManager.find(JpaCurrentQuota.class, quotaRoot.getValue());
    }
}
