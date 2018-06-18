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
package org.apache.james.mailbox.jpa;

import java.util.Date;
import java.util.List;

import javax.mail.Flags;
import javax.mail.internet.SharedInputStream;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAMailboxMessage;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.mailbox.store.ImmutableMailboxMessage;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.search.MessageSearchIndex;

/**
 * Abstract base class which should be used from JPA implementations.
 */
public class JPAMessageManager extends StoreMessageManager {
    
    public JPAMessageManager(MailboxSessionMapperFactory mapperFactory,
                             MessageSearchIndex index,
                             final MailboxEventDispatcher dispatcher,
                             MailboxPathLocker locker,
                             final Mailbox mailbox,
                             QuotaManager quotaManager,
                             QuotaRootResolver quotaRootResolver,
                             MessageParser messageParser,
                             MessageId.Factory messageIdFactory,
                             BatchSizes batchSizes,
                             ImmutableMailboxMessage.Factory immutableMailboxMessageFactory,
                             StoreRightManager storeRightManager) throws MailboxException {

        super(JPAMailboxManager.DEFAULT_NO_MESSAGE_CAPABILITIES, mapperFactory, index, dispatcher, locker, mailbox,
            quotaManager, quotaRootResolver, messageParser, messageIdFactory, batchSizes, immutableMailboxMessageFactory, storeRightManager);
    }
    
    @Override
    protected MailboxMessage createMessage(Date internalDate, int size, int bodyStartOctet, SharedInputStream content,
                                                  final Flags flags, PropertyBuilder propertyBuilder, List<MessageAttachment> attachments) throws MailboxException {

        return new JPAMailboxMessage((JPAMailbox) getMailboxEntity(), internalDate, size, flags, content,  bodyStartOctet,  propertyBuilder);
    }


    /**
     * Support user flags
     */
    @Override
    protected Flags getPermanentFlags(MailboxSession session) {
        Flags flags =  super.getPermanentFlags(session);
        flags.add(Flags.Flag.USER);
        return flags;
    }
    
}
