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

package org.apache.james.adapter.mailbox;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.mailbox.store.mail.model.SerializableQuota;
import org.apache.james.mailbox.store.mail.model.SerializableQuotaValue;

public interface QuotaManagementMBean {
    String getQuotaRoot(String namespace, String user, String name) throws MailboxException;

    SerializableQuota<QuotaCount> getMessageCountQuota(String quotaRoot) throws MailboxException;

    SerializableQuota<QuotaSize> getStorageQuota(String quotaRoot) throws MailboxException;

    SerializableQuotaValue<QuotaCount> getMaxMessageCount(String quotaRoot) throws MailboxException;

    SerializableQuotaValue<QuotaSize> getMaxStorage(String quotaRoot) throws MailboxException;

    SerializableQuotaValue<QuotaCount> getGlobalMaxMessageCount() throws MailboxException;

    SerializableQuotaValue<QuotaSize> getGlobalMaxStorage() throws MailboxException;

    void setMaxMessageCount(String quotaRoot, SerializableQuotaValue<QuotaCount> maxMessageCount) throws MailboxException;

    void setMaxStorage(String quotaRoot, SerializableQuotaValue<QuotaSize> maxSize) throws MailboxException;

    void setGlobalMaxMessageCount(SerializableQuotaValue<QuotaCount> maxGlobalMessageCount) throws MailboxException;

    void setGlobalMaxStorage(SerializableQuotaValue<QuotaSize> maxGlobalSize) throws MailboxException;
}
