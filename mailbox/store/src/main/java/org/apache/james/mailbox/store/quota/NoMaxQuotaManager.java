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

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;

/**
 * A Max Quota Manager that simply throws exceptions
 *
 * Intended to be used to disactivate Max Quota admin support
 */
public class NoMaxQuotaManager implements MaxQuotaManager {

    @Override
    public void setMaxStorage(QuotaRoot quotaRoot, long maxStorageQuota) throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, long maxMessageCount) throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public void setDefaultMaxStorage(long defaultMaxStorage) throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public void setDefaultMaxMessage(long defaultMaxMessageCount) throws MailboxException {
        throw new MailboxException("Operation is not supported");
    }

    @Override
    public long getMaxStorage(QuotaRoot quotaRoot) throws MailboxException {
        return Quota.UNLIMITED;
    }

    @Override
    public long getMaxMessage(QuotaRoot quotaRoot) throws MailboxException {
        return Quota.UNLIMITED;
    }

    @Override
    public long getDefaultMaxStorage() throws MailboxException {
        return Quota.UNLIMITED;
    }

    @Override
    public long getDefaultMaxMessage() throws MailboxException {
        return Quota.UNLIMITED;
    }
}
