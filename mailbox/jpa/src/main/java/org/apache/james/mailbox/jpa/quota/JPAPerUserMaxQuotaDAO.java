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

import org.apache.james.mailbox.jpa.quota.model.MaxDomainMessageCount;
import org.apache.james.mailbox.jpa.quota.model.MaxDomainStorage;
import org.apache.james.mailbox.jpa.quota.model.MaxGlobalMessageCount;
import org.apache.james.mailbox.jpa.quota.model.MaxGlobalStorage;
import org.apache.james.mailbox.jpa.quota.model.MaxUserMessageCount;
import org.apache.james.mailbox.jpa.quota.model.MaxUserStorage;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.mailbox.quota.QuotaValue;

public class JPAPerUserMaxQuotaDAO {

    private static final long INFINITE = -1;
    private final EntityManager entityManager;

    @Inject
    public JPAPerUserMaxQuotaDAO(EntityManagerFactory entityManagerFactory) {
        entityManager = entityManagerFactory.createEntityManager();
    }

    public void setMaxStorage(QuotaRoot quotaRoot, Optional<QuotaSize> maxStorageQuota) {
        entityManager.getTransaction().begin();
        MaxUserStorage storedValue = getMaxUserStorageEntity(quotaRoot, maxStorageQuota);
        entityManager.persist(storedValue);
        entityManager.getTransaction().commit();
    }

    private MaxUserStorage getMaxUserStorageEntity(QuotaRoot quotaRoot, Optional<QuotaSize> maxStorageQuota) {
        MaxUserStorage storedValue = entityManager.find(MaxUserStorage.class, quotaRoot.getValue());
        Long value = quotaValueToLong(maxStorageQuota);
        if (storedValue == null) {
            return new MaxUserStorage(quotaRoot.getValue(), value);
        }
        storedValue.setValue(value);
        return storedValue;
    }

    public void setMaxMessage(QuotaRoot quotaRoot, Optional<QuotaCount> maxMessageCount) {
        entityManager.getTransaction().begin();
        MaxUserMessageCount storedValue = getMaxUserMessageEntity(quotaRoot, maxMessageCount);
        entityManager.persist(storedValue);
        entityManager.getTransaction().commit();
    }

    private MaxUserMessageCount getMaxUserMessageEntity(QuotaRoot quotaRoot, Optional<QuotaCount> maxMessageQuota) {
        MaxUserMessageCount storedValue = entityManager.find(MaxUserMessageCount.class, quotaRoot.getValue());
        Long value = quotaValueToLong(maxMessageQuota);
        if (storedValue == null) {
            return new MaxUserMessageCount(quotaRoot.getValue(), value);
        }
        storedValue.setValue(value);
        return storedValue;
    }

    public void setDomainMaxMessage(String domain, Optional<QuotaCount> count) {
        entityManager.getTransaction().begin();
        MaxDomainMessageCount storedValue = getMaxDomainMessageEntity(domain, count);
        entityManager.persist(storedValue);
        entityManager.getTransaction().commit();
    }


    public void setDomainMaxStorage(String domain, Optional<QuotaSize> size) {
        entityManager.getTransaction().begin();
        MaxDomainStorage storedValue = getMaxDomainStorageEntity(domain, size);
        entityManager.persist(storedValue);
        entityManager.getTransaction().commit();
    }

    private MaxDomainMessageCount getMaxDomainMessageEntity(String domain, Optional<QuotaCount> maxMessageQuota) {
        MaxDomainMessageCount storedValue = entityManager.find(MaxDomainMessageCount.class, domain);
        Long value = quotaValueToLong(maxMessageQuota);
        if (storedValue == null) {
            return new MaxDomainMessageCount(domain, value);
        }
        storedValue.setValue(value);
        return storedValue;
    }

    private MaxDomainStorage getMaxDomainStorageEntity(String domain, Optional<QuotaSize> maxStorageQuota) {
        MaxDomainStorage storedValue = entityManager.find(MaxDomainStorage.class, domain);
        Long value = quotaValueToLong(maxStorageQuota);
        if (storedValue == null) {
            return new MaxDomainStorage(domain, value);
        }
        storedValue.setValue(value);
        return storedValue;
    }


    public void setGlobalMaxStorage(Optional<QuotaSize> globalMaxStorage) {
        entityManager.getTransaction().begin();
        MaxGlobalStorage globalMaxStorageEntity = getGlobalMaxStorageEntity(globalMaxStorage);
        entityManager.persist(globalMaxStorageEntity);
        entityManager.getTransaction().commit();
    }

    private MaxGlobalStorage getGlobalMaxStorageEntity(Optional<QuotaSize> maxSizeQuota) {
        MaxGlobalStorage storedValue = entityManager.find(MaxGlobalStorage.class, MaxGlobalStorage.DEFAULT_KEY);
        Long value = quotaValueToLong(maxSizeQuota);
        if (storedValue == null) {
            return new MaxGlobalStorage(value);
        }
        storedValue.setValue(value);
        return storedValue;
    }

    public void setGlobalMaxMessage(Optional<QuotaCount> globalMaxMessageCount) {
        entityManager.getTransaction().begin();
        MaxGlobalMessageCount globalMaxMessageEntity = getGlobalMaxMessageEntity(globalMaxMessageCount);
        entityManager.persist(globalMaxMessageEntity);
        entityManager.getTransaction().commit();
    }

    private MaxGlobalMessageCount getGlobalMaxMessageEntity(Optional<QuotaCount> maxMessageQuota) {
        MaxGlobalMessageCount storedValue = entityManager.find(MaxGlobalMessageCount.class, MaxGlobalMessageCount.DEFAULT_KEY);
        Long value = quotaValueToLong(maxMessageQuota);
        if (storedValue == null) {
            return new MaxGlobalMessageCount(value);
        }
        storedValue.setValue(value);
        return storedValue;
    }

    public Optional<QuotaSize> getGlobalMaxStorage() {
        MaxGlobalStorage storedValue = entityManager.find(MaxGlobalStorage.class, MaxGlobalStorage.DEFAULT_KEY);
        if (storedValue == null) {
            return Optional.empty();
        }
        return longToQuotaSize(storedValue.getValue());
    }

    public Optional<QuotaCount> getGlobalMaxMessage() {
        MaxGlobalMessageCount storedValue = entityManager.find(MaxGlobalMessageCount.class, MaxGlobalMessageCount.DEFAULT_KEY);
        if (storedValue == null) {
            return Optional.empty();
        }
        return longToQuotaCount(storedValue.getValue());
    }

    public Optional<QuotaSize> getMaxStorage(QuotaRoot quotaRoot) {
        MaxUserStorage storedValue = entityManager.find(MaxUserStorage.class, quotaRoot.getValue());
        if (storedValue == null) {
            return Optional.empty();
        }
        return longToQuotaSize(storedValue.getValue());
    }

    public Optional<QuotaCount> getMaxMessage(QuotaRoot quotaRoot) {
        MaxUserMessageCount storedValue = entityManager.find(MaxUserMessageCount.class, quotaRoot.getValue());
        if (storedValue == null) {
            return Optional.empty();
        }
        return longToQuotaCount(storedValue.getValue());
    }

    public Optional<QuotaCount> getDomainMaxMessage(String domain) {
        MaxDomainMessageCount storedValue = entityManager.find(MaxDomainMessageCount.class, domain);
        if (storedValue == null) {
            return Optional.empty();
        }
        return longToQuotaCount(storedValue.getValue());
    }

    public Optional<QuotaSize> getDomainMaxStorage(String domain) {
        MaxDomainStorage storedValue = entityManager.find(MaxDomainStorage.class, domain);
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
