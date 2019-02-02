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
import java.util.function.Function;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.james.backends.jpa.TransactionRunner;
import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.core.quota.QuotaValue;
import org.apache.james.mailbox.jpa.quota.model.MaxDomainMessageCount;
import org.apache.james.mailbox.jpa.quota.model.MaxDomainStorage;
import org.apache.james.mailbox.jpa.quota.model.MaxGlobalMessageCount;
import org.apache.james.mailbox.jpa.quota.model.MaxGlobalStorage;
import org.apache.james.mailbox.jpa.quota.model.MaxUserMessageCount;
import org.apache.james.mailbox.jpa.quota.model.MaxUserStorage;
import org.apache.james.mailbox.model.QuotaRoot;

public class JPAPerUserMaxQuotaDAO {

    private static final long INFINITE = -1;
    private final EntityManagerFactory entityManagerFactory;
    private final TransactionRunner transactionRunner;

    @Inject
    public JPAPerUserMaxQuotaDAO(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
        this.transactionRunner = new TransactionRunner(entityManagerFactory);
    }

    public void setMaxStorage(QuotaRoot quotaRoot, Optional<QuotaSize> maxStorageQuota) {
        transactionRunner.run(
            entityManager -> {
                MaxUserStorage storedValue = getMaxUserStorageEntity(entityManager, quotaRoot, maxStorageQuota);
                entityManager.persist(storedValue);
            });
    }

    private MaxUserStorage getMaxUserStorageEntity(EntityManager entityManager, QuotaRoot quotaRoot, Optional<QuotaSize> maxStorageQuota) {
        MaxUserStorage storedValue = entityManager.find(MaxUserStorage.class, quotaRoot.getValue());
        Long value = quotaValueToLong(maxStorageQuota);
        if (storedValue == null) {
            return new MaxUserStorage(quotaRoot.getValue(), value);
        }
        storedValue.setValue(value);
        return storedValue;
    }

    public void setMaxMessage(QuotaRoot quotaRoot, Optional<QuotaCount> maxMessageCount) {
        transactionRunner.run(
            entityManager -> {
                MaxUserMessageCount storedValue = getMaxUserMessageEntity(entityManager, quotaRoot, maxMessageCount);
                entityManager.persist(storedValue);
            });
    }

    private MaxUserMessageCount getMaxUserMessageEntity(EntityManager entityManager, QuotaRoot quotaRoot, Optional<QuotaCount> maxMessageQuota) {
        MaxUserMessageCount storedValue = entityManager.find(MaxUserMessageCount.class, quotaRoot.getValue());
        Long value = quotaValueToLong(maxMessageQuota);
        if (storedValue == null) {
            return new MaxUserMessageCount(quotaRoot.getValue(), value);
        }
        storedValue.setValue(value);
        return storedValue;
    }

    public void setDomainMaxMessage(Domain domain, Optional<QuotaCount> count) {
        transactionRunner.run(
            entityManager -> {
                MaxDomainMessageCount storedValue = getMaxDomainMessageEntity(entityManager, domain, count);
                entityManager.persist(storedValue);
            });
    }


    public void setDomainMaxStorage(Domain domain, Optional<QuotaSize> size) {
        transactionRunner.run(
            entityManager -> {
                MaxDomainStorage storedValue = getMaxDomainStorageEntity(entityManager, domain, size);
                entityManager.persist(storedValue);
            });
    }

    private MaxDomainMessageCount getMaxDomainMessageEntity(EntityManager entityManager, Domain domain, Optional<QuotaCount> maxMessageQuota) {
        MaxDomainMessageCount storedValue = entityManager.find(MaxDomainMessageCount.class, domain.asString());
        Long value = quotaValueToLong(maxMessageQuota);
        if (storedValue == null) {
            return new MaxDomainMessageCount(domain, value);
        }
        storedValue.setValue(value);
        return storedValue;
    }

    private MaxDomainStorage getMaxDomainStorageEntity(EntityManager entityManager, Domain domain, Optional<QuotaSize> maxStorageQuota) {
        MaxDomainStorage storedValue = entityManager.find(MaxDomainStorage.class, domain.asString());
        Long value = quotaValueToLong(maxStorageQuota);
        if (storedValue == null) {
            return new MaxDomainStorage(domain, value);
        }
        storedValue.setValue(value);
        return storedValue;
    }


    public void setGlobalMaxStorage(Optional<QuotaSize> globalMaxStorage) {
        transactionRunner.run(
            entityManager -> {
                MaxGlobalStorage globalMaxStorageEntity = getGlobalMaxStorageEntity(entityManager, globalMaxStorage);
                entityManager.persist(globalMaxStorageEntity);
            });
    }

    private MaxGlobalStorage getGlobalMaxStorageEntity(EntityManager entityManager, Optional<QuotaSize> maxSizeQuota) {
        MaxGlobalStorage storedValue = entityManager.find(MaxGlobalStorage.class, MaxGlobalStorage.DEFAULT_KEY);
        Long value = quotaValueToLong(maxSizeQuota);
        if (storedValue == null) {
            return new MaxGlobalStorage(value);
        }
        storedValue.setValue(value);
        return storedValue;
    }

    public void setGlobalMaxMessage(Optional<QuotaCount> globalMaxMessageCount) {
        transactionRunner.run(
            entityManager -> {
                MaxGlobalMessageCount globalMaxMessageEntity = getGlobalMaxMessageEntity(entityManager, globalMaxMessageCount);
                entityManager.persist(globalMaxMessageEntity);
            });
    }

    private MaxGlobalMessageCount getGlobalMaxMessageEntity(EntityManager entityManager, Optional<QuotaCount> maxMessageQuota) {
        MaxGlobalMessageCount storedValue = entityManager.find(MaxGlobalMessageCount.class, MaxGlobalMessageCount.DEFAULT_KEY);
        Long value = quotaValueToLong(maxMessageQuota);
        if (storedValue == null) {
            return new MaxGlobalMessageCount(value);
        }
        storedValue.setValue(value);
        return storedValue;
    }

    public Optional<QuotaSize> getGlobalMaxStorage() {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        MaxGlobalStorage storedValue = entityManager.find(MaxGlobalStorage.class, MaxGlobalStorage.DEFAULT_KEY);
        if (storedValue == null) {
            return Optional.empty();
        }
        return longToQuotaSize(storedValue.getValue());
    }

    public Optional<QuotaCount> getGlobalMaxMessage() {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        MaxGlobalMessageCount storedValue = entityManager.find(MaxGlobalMessageCount.class, MaxGlobalMessageCount.DEFAULT_KEY);
        if (storedValue == null) {
            return Optional.empty();
        }
        return longToQuotaCount(storedValue.getValue());
    }

    public Optional<QuotaSize> getMaxStorage(QuotaRoot quotaRoot) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        MaxUserStorage storedValue = entityManager.find(MaxUserStorage.class, quotaRoot.getValue());
        if (storedValue == null) {
            return Optional.empty();
        }
        return longToQuotaSize(storedValue.getValue());
    }

    public Optional<QuotaCount> getMaxMessage(QuotaRoot quotaRoot) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        MaxUserMessageCount storedValue = entityManager.find(MaxUserMessageCount.class, quotaRoot.getValue());
        if (storedValue == null) {
            return Optional.empty();
        }
        return longToQuotaCount(storedValue.getValue());
    }

    public Optional<QuotaCount> getDomainMaxMessage(Domain domain) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        MaxDomainMessageCount storedValue = entityManager.find(MaxDomainMessageCount.class, domain.asString());
        if (storedValue == null) {
            return Optional.empty();
        }
        return longToQuotaCount(storedValue.getValue());
    }

    public Optional<QuotaSize> getDomainMaxStorage(Domain domain) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        MaxDomainStorage storedValue = entityManager.find(MaxDomainStorage.class, domain.asString());
        if (storedValue == null) {
            return Optional.empty();
        }
        return longToQuotaSize(storedValue.getValue());
    }


    private Long quotaValueToLong(Optional<? extends QuotaValue<?>> maxStorageQuota) {
        return maxStorageQuota.map(value -> {
            if (value.isUnlimited()) {
                return INFINITE;
            }
            return value.asLong();
        }).orElse(null);
    }

    private Optional<QuotaSize> longToQuotaSize(Long value) {
        return longToQuotaValue(value, QuotaSize.unlimited(), QuotaSize::size);
    }

    private Optional<QuotaCount> longToQuotaCount(Long value) {
        return longToQuotaValue(value, QuotaCount.unlimited(), QuotaCount::count);
    }

    private <T extends QuotaValue<T>> Optional<T> longToQuotaValue(Long value, T infiniteValue, Function<Long, T> quotaFactory) {
        if (value == null) {
            return Optional.empty();
        }
        if (value == INFINITE) {
            return Optional.of(infiniteValue);
        }
        return Optional.of(quotaFactory.apply(value));
    }

}
