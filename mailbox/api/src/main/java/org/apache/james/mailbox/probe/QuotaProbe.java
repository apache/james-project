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

package org.apache.james.mailbox.probe;

import java.util.Optional;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;

public interface QuotaProbe {

    String getQuotaRoot(String namespace, String user, String name) throws MailboxException;

    Quota<QuotaCountLimit, QuotaCountUsage> getMessageCountQuota(String quotaRoot) throws MailboxException;

    Quota<QuotaSizeLimit, QuotaSizeUsage> getStorageQuota(String quotaRoot) throws MailboxException;

    Optional<QuotaCountLimit> getMaxMessageCount(String quotaRoot) throws MailboxException;

    Optional<QuotaSizeLimit> getMaxStorage(String quotaRoot) throws MailboxException;

    Optional<QuotaCountLimit> getGlobalMaxMessageCount() throws MailboxException;

    Optional<QuotaSizeLimit> getGlobalMaxStorage() throws MailboxException;

    void setMaxMessageCount(String quotaRoot, QuotaCountLimit maxMessageCount) throws MailboxException;

    void setMaxStorage(String quotaRoot, QuotaSizeLimit maxSize) throws MailboxException;

    void setGlobalMaxMessageCount(QuotaCountLimit maxGlobalMessageCount) throws MailboxException;

    void setGlobalMaxStorage(QuotaSizeLimit maxGlobalSize) throws MailboxException;

}