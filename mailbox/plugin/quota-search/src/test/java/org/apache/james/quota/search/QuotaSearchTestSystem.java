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

package org.apache.james.quota.search;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.user.api.UsersRepository;

public class QuotaSearchTestSystem {
    private final MaxQuotaManager maxQuotaManager;
    private final MailboxManager mailboxManager;
    private final QuotaSearcher quotaSearcher;
    private final UsersRepository usersRepository;
    private final DomainList domainList;
    private final Runnable await;

    public QuotaSearchTestSystem(MaxQuotaManager maxQuotaManager, MailboxManager mailboxManager, QuotaSearcher quotaSearcher, UsersRepository usersRepository, DomainList domainList, Runnable await) {
        this.maxQuotaManager = maxQuotaManager;
        this.mailboxManager = mailboxManager;
        this.quotaSearcher = quotaSearcher;
        this.usersRepository = usersRepository;
        this.domainList = domainList;
        this.await = await;
    }

    public MaxQuotaManager getMaxQuotaManager() {
        return maxQuotaManager;
    }

    public MailboxManager getMailboxManager() {
        return mailboxManager;
    }

    public QuotaSearcher getQuotaSearcher() {
        return quotaSearcher;
    }

    public UsersRepository getUsersRepository() {
        return usersRepository;
    }

    public DomainList getDomainList() {
        return domainList;
    }

    public void await() {
        await.run();
    }
}
