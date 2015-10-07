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

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.jcr.mail.JCRMailboxMapper;
import org.apache.james.mailbox.jcr.mail.JCRMessageMapper;
import org.apache.james.mailbox.jcr.user.JCRSubscriptionMapper;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.user.SubscriptionMapper;

/**
 * JCR implementation of a {@link MailboxSessionMapperFactory}
 * 
 *
 */
public class JCRMailboxSessionMapperFactory extends MailboxSessionMapperFactory<JCRId> {

    private final MailboxSessionJCRRepository repository;
    private final static int DEFAULT_SCALING = 2;
    private final int scaling;
    private int messageScaling;
    private UidProvider<JCRId> uidProvider;
    private ModSeqProvider<JCRId> modSeqProvider;

    public JCRMailboxSessionMapperFactory(final MailboxSessionJCRRepository repository, final UidProvider<JCRId> uidProvider, final ModSeqProvider<JCRId> modSeqProvider) {
        this(repository, uidProvider, modSeqProvider, DEFAULT_SCALING, JCRMessageMapper.MESSAGE_SCALE_DAY);
    }

    public JCRMailboxSessionMapperFactory(final MailboxSessionJCRRepository repository,  final UidProvider<JCRId> uidProvider, final ModSeqProvider<JCRId> modSeqProvider, final int scaling, final int messageScaling) {
        this.repository = repository;
        this.scaling = scaling;
        this.messageScaling = messageScaling;
        this.uidProvider= uidProvider;
        this.modSeqProvider = modSeqProvider;
    }
    
    @Override
    public MailboxMapper<JCRId> createMailboxMapper(MailboxSession session) throws MailboxException {
        JCRMailboxMapper mapper = new JCRMailboxMapper(repository, session, scaling);
        return mapper;
    }

    @Override
    public MessageMapper<JCRId> createMessageMapper(MailboxSession session) throws MailboxException {
        JCRMessageMapper messageMapper = new JCRMessageMapper(repository, session, uidProvider, modSeqProvider,  messageScaling);
        return messageMapper;
    }

    @Override
    public SubscriptionMapper createSubscriptionMapper(MailboxSession session) throws SubscriptionException {
        JCRSubscriptionMapper mapper = new JCRSubscriptionMapper(repository, session, DEFAULT_SCALING);
        return mapper;
    }
    
    public MailboxSessionJCRRepository getRepository() {
        return repository;
    }

}
