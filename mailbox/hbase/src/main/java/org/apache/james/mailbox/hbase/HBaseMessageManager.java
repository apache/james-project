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
package org.apache.james.mailbox.hbase;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.MailboxEventDispatcher;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.search.MessageSearchIndex;

/**
 * HBase implementation of MessageManager.
 * 
 */
public class HBaseMessageManager extends StoreMessageManager<HBaseId> {

    public HBaseMessageManager(MailboxSessionMapperFactory<HBaseId> mapperFactory, MessageSearchIndex<HBaseId> index,
            MailboxEventDispatcher<HBaseId> dispatcher, MailboxPathLocker locker, Mailbox<HBaseId> mailbox, MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver, QuotaManager quotaManager, QuotaRootResolver quotaRootResolver) throws MailboxException {
        super(mapperFactory, index, dispatcher, locker, mailbox, aclResolver, groupMembershipResolver, quotaManager, quotaRootResolver);

    }

    /**
     * Support user flags
     */
    @Override
    protected Flags getPermanentFlags(MailboxSession session) {
        Flags flags = super.getPermanentFlags(session);
        flags.add(Flags.Flag.USER);
        return flags;
    }
}
