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
package org.apache.james.mailbox.quota;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.reactivestreams.Publisher;


/**
 * Allows to get quotas for {@link QuotaRoot} which are bound to a user.
 * Part of RFC 2087 implementation
 */
public interface QuotaManager {
    class Quotas {
        private final Quota<QuotaCountLimit, QuotaCountUsage> messageQuota;
        private final Quota<QuotaSizeLimit, QuotaSizeUsage> storageQuota;

        public Quotas(Quota<QuotaCountLimit, QuotaCountUsage> messageQuota, Quota<QuotaSizeLimit, QuotaSizeUsage> storageQuota) {
            this.messageQuota = messageQuota;
            this.storageQuota = storageQuota;
        }

        public Quota<QuotaCountLimit, QuotaCountUsage> getMessageQuota() {
            return messageQuota;
        }

        public Quota<QuotaSizeLimit, QuotaSizeUsage> getStorageQuota() {
            return storageQuota;
        }
    }

    /**
     * Return the message count {@link Quota} for the given {@link QuotaRoot} (which in fact is
     * bound to a user)
     *
     * @param quotaRoot Quota root argument from RFC 2087 ( correspond to the user owning this mailbox )
     */
    Quota<QuotaCountLimit, QuotaCountUsage> getMessageQuota(QuotaRoot quotaRoot) throws MailboxException;


    /**
     * Return the message storage {@link Quota} for the given {@link QuotaRoot} (which in fact is
     * bound to a user)
     *
     * @param quotaRoot Quota root argument from RFC 2087 ( correspond to the user owning this mailbox )
     */
    Quota<QuotaSizeLimit, QuotaSizeUsage> getStorageQuota(QuotaRoot quotaRoot) throws MailboxException;

    Quotas getQuotas(QuotaRoot quotaRoot) throws MailboxException;

    Publisher<Quotas> getQuotasReactive(QuotaRoot quotaRoot);
}
