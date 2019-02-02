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

package org.apache.james.mailbox.store.quota;

import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.mail.model.Mailbox;

public class QuotaChecker {

    private final Quota<QuotaCount> messageQuota;
    private final Quota<QuotaSize> sizeQuota;
    private final QuotaRoot quotaRoot;

    public QuotaChecker(QuotaManager quotaManager, QuotaRootResolver quotaRootResolver, Mailbox mailbox) throws MailboxException {
        this.quotaRoot = quotaRootResolver.getQuotaRoot(mailbox.generateAssociatedPath());
        this.messageQuota = quotaManager.getMessageQuota(quotaRoot);
        this.sizeQuota = quotaManager.getStorageQuota(quotaRoot);
    }

    public QuotaChecker(Quota<QuotaCount> messageQuota, Quota<QuotaSize> sizeQuota, QuotaRoot quotaRoot) {
        this.messageQuota = messageQuota;
        this.sizeQuota = sizeQuota;
        this.quotaRoot = quotaRoot;
    }

    public void tryAddition(long count, long size) throws OverQuotaException {
        tryCountAddition(count);
        trySizeAddition(size);
    }

    private void trySizeAddition(long size) throws OverQuotaException {
        Quota<QuotaSize> afterAdditionQuotaSize = sizeQuota.addValueToQuota(QuotaSize.size(size));
        if (afterAdditionQuotaSize.isOverQuota()) {
            throw new OverQuotaException(
                "You use too much space in " + quotaRoot.getValue(),
                afterAdditionQuotaSize.getLimit(),
                afterAdditionQuotaSize.getUsed());
        }
    }

    private void tryCountAddition(long count) throws OverQuotaException {
        Quota<QuotaCount> afterAdditionQuotaCount = messageQuota.addValueToQuota(QuotaCount.count(count));
        if (afterAdditionQuotaCount.isOverQuota()) {
            throw new OverQuotaException(
                "You have too many messages in " + quotaRoot.getValue(),
                messageQuota.getLimit(),
                messageQuota.getUsed());
        }
    }

}
