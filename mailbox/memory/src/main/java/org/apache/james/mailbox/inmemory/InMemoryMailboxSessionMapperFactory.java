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
package org.apache.james.mailbox.inmemory;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.inmemory.mail.InMemoryMailboxMapper;
import org.apache.james.mailbox.inmemory.mail.InMemoryMessageMapper;
import org.apache.james.mailbox.inmemory.mail.InMemoryModSeqProvider;
import org.apache.james.mailbox.inmemory.mail.InMemoryUidProvider;
import org.apache.james.mailbox.inmemory.user.InMemorySubscriptionMapper;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;

public class InMemoryMailboxSessionMapperFactory extends MailboxSessionMapperFactory<InMemoryId> {

    private MailboxMapper<InMemoryId> mailboxMapper;
    private MessageMapper<InMemoryId> messageMapper;
    private SubscriptionMapper subscriptionMapper;
    
    public InMemoryMailboxSessionMapperFactory() {
        mailboxMapper = new InMemoryMailboxMapper();
        messageMapper = new InMemoryMessageMapper(null, new InMemoryUidProvider(), new InMemoryModSeqProvider());
        subscriptionMapper = new InMemorySubscriptionMapper();
    }
    
    @Override
    public MailboxMapper<InMemoryId> createMailboxMapper(MailboxSession session) throws MailboxException {
        return mailboxMapper;
    }

    @Override
    public MessageMapper<InMemoryId> createMessageMapper(MailboxSession session) throws MailboxException {
        return messageMapper;
    }

    @Override
    public SubscriptionMapper createSubscriptionMapper(MailboxSession session) throws SubscriptionException {
        return subscriptionMapper;
    }

    public void deleteAll() throws MailboxException {
        ((InMemoryMailboxMapper) mailboxMapper).deleteAll();
        ((InMemoryMessageMapper) messageMapper).deleteAll();
        ((InMemorySubscriptionMapper) subscriptionMapper).deleteAll();
    }

}
