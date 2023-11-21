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

import java.time.Clock;

import jakarta.inject.Inject;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.mail.InMemoryAnnotationMapper;
import org.apache.james.mailbox.inmemory.mail.InMemoryAttachmentMapper;
import org.apache.james.mailbox.inmemory.mail.InMemoryMailboxMapper;
import org.apache.james.mailbox.inmemory.mail.InMemoryMessageIdMapper;
import org.apache.james.mailbox.inmemory.mail.InMemoryMessageMapper;
import org.apache.james.mailbox.inmemory.mail.InMemoryModSeqProvider;
import org.apache.james.mailbox.inmemory.mail.InMemoryUidProvider;
import org.apache.james.mailbox.inmemory.user.InMemorySubscriptionMapper;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.user.SubscriptionMapper;

public class InMemoryMailboxSessionMapperFactory extends MailboxSessionMapperFactory implements AttachmentMapperFactory {

    private final MailboxMapper mailboxMapper;
    private final InMemoryMessageMapper messageMapper;
    private final InMemoryMessageIdMapper messageIdMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final AttachmentMapper attachmentMapper;
    private final AnnotationMapper annotationMapper;
    private final InMemoryUidProvider uidProvider;
    private final InMemoryModSeqProvider modSeqProvider;

    @Inject
    public InMemoryMailboxSessionMapperFactory(Clock clock) {
        mailboxMapper = new InMemoryMailboxMapper();
        uidProvider = new InMemoryUidProvider();
        modSeqProvider = new InMemoryModSeqProvider();
        messageMapper = new InMemoryMessageMapper(null, uidProvider, modSeqProvider, clock);
        messageIdMapper = new InMemoryMessageIdMapper(mailboxMapper, messageMapper);

        subscriptionMapper = new InMemorySubscriptionMapper();
        attachmentMapper = new InMemoryAttachmentMapper();
        annotationMapper = new InMemoryAnnotationMapper();
    }
    
    @Override
    public MailboxMapper createMailboxMapper(MailboxSession session) {
        return mailboxMapper;
    }

    @Override
    public InMemoryMessageMapper createMessageMapper(MailboxSession session) {
        return messageMapper;
    }

    @Override
    public MessageIdMapper createMessageIdMapper(MailboxSession session) {
        return messageIdMapper;
    }

    @Override
    public SubscriptionMapper createSubscriptionMapper(MailboxSession session) {
        return subscriptionMapper;
    }
    
    @Override
    public AttachmentMapper createAttachmentMapper(MailboxSession session) {
        return attachmentMapper;
    }

    public void deleteAll() throws MailboxException {
        ((InMemoryMailboxMapper) mailboxMapper).deleteAll().block();
        ((InMemoryMessageMapper) messageMapper).deleteAll();
        ((InMemorySubscriptionMapper) subscriptionMapper).deleteAll();
    }

    @Override
    public AnnotationMapper createAnnotationMapper(MailboxSession session) {
        return annotationMapper;
    }

    @Override
    public UidProvider getUidProvider(MailboxSession session) {
        return uidProvider;
    }

    @Override
    public ModSeqProvider getModSeqProvider(MailboxSession session) {
        return modSeqProvider;
    }

    @Override
    public AttachmentMapper getAttachmentMapper(MailboxSession session) {
        return attachmentMapper;
    }

}
