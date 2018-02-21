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

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.quota.model.JpaCurrentQuota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.mailbox.store.quota.StoreCurrentQuotaManager;

import com.google.common.base.Preconditions;

public class JpaCurrentQuotaManager implements StoreCurrentQuotaManager {

    public static final long NO_MESSAGES = 0L;
    public static final long NO_STORED_BYTES = 0L;

    private final EntityManager entityManager;

    @Inject
    public JpaCurrentQuotaManager(EntityManagerFactory entityManagerFactory) {
        this.entityManager = entityManagerFactory.createEntityManager();
    }

    @Override
    public MailboxListener.ListenerType getAssociatedListenerType() {
        return MailboxListener.ListenerType.ONCE;
    }

    @Override
    public QuotaCount getCurrentMessageCount(QuotaRoot quotaRoot) throws MailboxException {
        JpaCurrentQuota userQuota = retrieveUserQuota(quotaRoot);

        if (userQuota == null) {
            return QuotaCount.count(NO_STORED_BYTES);
        }
        return QuotaCount.count(userQuota.getMessageCount());
    }

    @Override
    public QuotaSize getCurrentStorage(QuotaRoot quotaRoot) throws MailboxException {
        JpaCurrentQuota userQuota = retrieveUserQuota(quotaRoot);

        if (userQuota == null) {
            return QuotaSize.size(NO_STORED_BYTES);
        }
        return QuotaSize.size(userQuota.getSize());
    }

    @Override
    public void increase(QuotaRoot quotaRoot, long count, long size) throws MailboxException {
        Preconditions.checkArgument(count > 0, "Counts should be positive");
        Preconditions.checkArgument(size > 0, "Size should be positive");

        JpaCurrentQuota jpaCurrentQuota = Optional.ofNullable(retrieveUserQuota(quotaRoot))
            .orElse(new JpaCurrentQuota(quotaRoot.getValue(), NO_MESSAGES, NO_STORED_BYTES));

        entityManager.merge(new JpaCurrentQuota(quotaRoot.getValue(),
            jpaCurrentQuota.getMessageCount() + count,
            jpaCurrentQuota.getSize() + size));
    }

    @Override
    public void decrease(QuotaRoot quotaRoot, long count, long size) throws MailboxException {
        Preconditions.checkArgument(count > 0, "Counts should be positive");
        Preconditions.checkArgument(size > 0, "Counts should be positive");

        JpaCurrentQuota jpaCurrentQuota = Optional.ofNullable(retrieveUserQuota(quotaRoot))
            .orElse(new JpaCurrentQuota(quotaRoot.getValue(), NO_MESSAGES, NO_STORED_BYTES));

        entityManager.merge(new JpaCurrentQuota(quotaRoot.getValue(),
            jpaCurrentQuota.getMessageCount() - count,
            jpaCurrentQuota.getSize() - size));
    }

    private JpaCurrentQuota retrieveUserQuota(QuotaRoot quotaRoot) throws MailboxException {
        return entityManager.find(JpaCurrentQuota.class, quotaRoot.getValue());
    }
}
