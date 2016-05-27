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
package org.apache.james.mailbox.jcr;

import java.util.Date;
import java.util.List;

import javax.mail.Flags;
import javax.mail.internet.SharedInputStream;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jcr.mail.model.JCRMailbox;
import org.apache.james.mailbox.jcr.mail.model.JCRMailboxMessage;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.Attachment;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.slf4j.Logger;

/**
 * JCR implementation of a {@link org.apache.james.mailbox.MessageManager}
 *
 */
public class JCRMessageManager extends StoreMessageManager {

    private final Logger log;

    public JCRMessageManager(MailboxSessionMapperFactory mapperFactory, MessageSearchIndex index, 
            final MailboxEventDispatcher dispatcher, MailboxPathLocker locker, JCRMailbox mailbox, MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver, Logger log, QuotaManager quotaManager, QuotaRootResolver quotaRootResolver) throws MailboxException {
        super(mapperFactory, index, dispatcher, locker, mailbox, aclResolver, groupMembershipResolver, quotaManager, quotaRootResolver);
        this.log = log;
    }


    @Override
    protected MailboxMessage createMessage(Date internalDate, int size, int bodyStartOctet, SharedInputStream content, Flags flags, PropertyBuilder propertyBuilder, List<Attachment> attachments) throws MailboxException{
        JCRId mailboxId = (JCRId) getMailboxEntity().getMailboxId();
        return new JCRMailboxMessage(mailboxId, internalDate,
                size, flags, content, bodyStartOctet, propertyBuilder, log);
    }

    /**
     * This implementation allow to store ANY user flag in a permanent manner
     */
    @Override
    protected Flags getPermanentFlags(MailboxSession session) {
        Flags perm =  super.getPermanentFlags(session);
        perm.add(Flags.Flag.USER);
        return perm;
    }
    
    
}