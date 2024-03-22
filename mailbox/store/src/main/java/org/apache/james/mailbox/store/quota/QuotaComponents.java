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

import jakarta.inject.Inject;

import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;

public class QuotaComponents {
    public static QuotaComponents disabled(SessionProvider sessionProvider, MailboxSessionMapperFactory mailboxSessionMapperFactory) {
        return new QuotaComponents(
            new NoMaxQuotaManager(),
            new NoQuotaManager(),
            new DefaultUserQuotaRootResolver(sessionProvider, mailboxSessionMapperFactory));
    }

    private final MaxQuotaManager maxQuotaManager;
    private final QuotaManager quotaManager;
    private final QuotaRootResolver quotaRootResolver;

    @Inject
    public QuotaComponents(MaxQuotaManager maxQuotaManager, QuotaManager quotaManager, QuotaRootResolver quotaRootResolver) {
        this.maxQuotaManager = maxQuotaManager;
        this.quotaManager = quotaManager;
        this.quotaRootResolver = quotaRootResolver;
    }

    public MaxQuotaManager getMaxQuotaManager() {
        return maxQuotaManager;
    }

    public QuotaManager getQuotaManager() {
        return quotaManager;
    }

    public QuotaRootResolver getQuotaRootResolver() {
        return quotaRootResolver;
    }
}
