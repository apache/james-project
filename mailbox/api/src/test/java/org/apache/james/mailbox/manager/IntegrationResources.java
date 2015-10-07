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

package org.apache.james.mailbox.manager;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.quota.QuotaRootResolver;

/**
 * Provides empty resources for integration tests.
 */
public interface IntegrationResources {

    MailboxManager createMailboxManager(GroupMembershipResolver groupMembershipResolver) throws MailboxException;

    QuotaManager createQuotaManager(MaxQuotaManager maxQuotaManager, MailboxManager mailboxManager) throws Exception;

    MaxQuotaManager createMaxQuotaManager() throws Exception;

    QuotaRootResolver createQuotaRootResolver(MailboxManager mailboxManager) throws Exception;

    GroupMembershipResolver createGroupMembershipResolver() throws Exception;

    /**
     * Init you will want to perform before tests
     *
     * @throws Exception
     */
    void init() throws Exception;

    void clean() throws Exception;

}