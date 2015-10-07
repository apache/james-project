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

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.QuotaRoot;

/**
 * This interface describe how to set the max quotas for users
 * Part of RFC 2087 implementation
 */
public interface MaxQuotaManager {

    /**
     * Method allowing you to set the maximum storage quota for a given user
     *
     * @param quotaRoot Quota root argument from RFC 2087 ( correspond to the user owning this mailbox )
     * @param maxStorageQuota The new storage quota ( in bytes ) for this user
     */
    void setMaxStorage(QuotaRoot quotaRoot, long maxStorageQuota) throws MailboxException;

    /**
     * Method allowing you to set the maximum message count allowed for this user
     *
     * @param quotaRoot Quota root argument from RFC 2087 ( correspond to the user owning this mailbox )
     * @param maxMessageCount The new message count allowed for this user.
     */
    void setMaxMessage(QuotaRoot quotaRoot, long maxMessageCount) throws MailboxException;

    /**
     * Method allowing you to set the default maximum storage in bytes.
     *
     * @param defaultMaxStorage new default maximum storage
     */
    void setDefaultMaxStorage(long defaultMaxStorage) throws MailboxException;

    /**
     * Method allowing you to set the default maximum message count allowed
     *
     * @param defaultMaxMessageCount new default message count
     */
    void setDefaultMaxMessage(long defaultMaxMessageCount) throws MailboxException;

    long getDefaultMaxStorage() throws MailboxException;

    long getDefaultMaxMessage() throws MailboxException;

    /**
     * Return the maximum storage which is allowed for the given {@link QuotaRoot} (in fact the user which the session is bound to)
     *
     * The returned valued must be in <strong>bytes</strong>
     *
     * @param quotaRoot Quota root argument from RFC 2087 ( correspond to the user owning this mailbox )
     * @return maxBytesThe maximum storage
     * @throws MailboxException
     */
    long getMaxStorage(QuotaRoot quotaRoot) throws MailboxException;


    /**
     * Return the maximum message count which is allowed for the given {@link QuotaRoot} (in fact the user which the session is bound to)
     *
     * @param quotaRoot Quota root argument from RFC 2087 ( correspond to the user owning this mailbox )
     * @return maximum of allowed message count
     * @throws MailboxException
     */
    long getMaxMessage(QuotaRoot quotaRoot) throws MailboxException;
}