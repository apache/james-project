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
package org.apache.james.mailbox.store;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.RequestAware;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapperFactory;

/**
 * Maintain mapper instances by {@link MailboxSession}. So only one mapper instance is used
 * in a {@link MailboxSession}
 */
public abstract class MailboxSessionMapperFactory implements RequestAware, MailboxMapperFactory, MessageMapperFactory, SubscriptionMapperFactory {

    protected static final String MESSAGEMAPPER = "MESSAGEMAPPER";
    protected static final String MESSAGEIDMAPPER = "MESSAGEIDMAPPER";
    protected static final String MAILBOXMAPPER = "MAILBOXMAPPER";
    protected static final String SUBSCRIPTIONMAPPER = "SUBSCRIPTIONMAPPER";
    protected static final String ANNOTATIONMAPPER = "ANNOTATIONMAPPER";
    
    
    @Override
    public MessageMapper getMessageMapper(MailboxSession session) {
        MessageMapper mapper = (MessageMapper) session.getAttributes().get(MESSAGEMAPPER);
        if (mapper == null) {
            mapper = createMessageMapper(session);
            session.getAttributes().put(MESSAGEMAPPER, mapper);
        }
        return mapper;
    }

    public MessageIdMapper getMessageIdMapper(MailboxSession session) {
        MessageIdMapper mapper = (MessageIdMapper) session.getAttributes().get(MESSAGEIDMAPPER);
        if (mapper == null) {
            mapper = createMessageIdMapper(session);
            session.getAttributes().put(MESSAGEIDMAPPER, mapper);
        }
        return mapper;
    }

    public AnnotationMapper getAnnotationMapper(MailboxSession session) {
        AnnotationMapper mapper = (AnnotationMapper)session.getAttributes().get(ANNOTATIONMAPPER);
        if (mapper == null) {
            mapper = createAnnotationMapper(session);
            session.getAttributes().put(ANNOTATIONMAPPER, mapper);
        }
        return mapper;
    }

    public abstract AnnotationMapper createAnnotationMapper(MailboxSession session);

    /**
     * Create a {@link MessageMapper} instance which will get reused during the whole {@link MailboxSession}
     * 
     * @return messageMapper
     */
    public abstract MessageMapper createMessageMapper(MailboxSession session);


    public abstract MessageIdMapper createMessageIdMapper(MailboxSession session);

    @Override
    public MailboxMapper getMailboxMapper(MailboxSession session) {
        MailboxMapper mapper = (MailboxMapper) session.getAttributes().get(MAILBOXMAPPER);
        if (mapper == null) {
            mapper = createMailboxMapper(session);
            session.getAttributes().put(MAILBOXMAPPER, mapper);
        }
        return mapper;
    }

    /**
     * Create a {@link MailboxMapper} instance which will get reused during the whole {@link MailboxSession}
     * 
     * @return mailboxMapper
     */
    public abstract MailboxMapper createMailboxMapper(MailboxSession session);

    /**
     * Create a {@link SubscriptionMapper} instance or return the one which exists for the {@link MailboxSession} already
     * 
     * @return mapper
     */
    @Override
    public SubscriptionMapper getSubscriptionMapper(MailboxSession session) {
        SubscriptionMapper mapper = (SubscriptionMapper) session.getAttributes().get(SUBSCRIPTIONMAPPER);
        if (mapper == null) {
            mapper = createSubscriptionMapper(session);
            session.getAttributes().put(SUBSCRIPTIONMAPPER, mapper);
        }
        return mapper;
    }
    
    /**
     * Create a {@link SubscriptionMapper} instance which will get reused during the whole {@link MailboxSession}
     *
     * @return subscriptionMapper
     */
    public abstract SubscriptionMapper createSubscriptionMapper(MailboxSession session);

    public abstract UidProvider getUidProvider(MailboxSession session);

    public abstract ModSeqProvider getModSeqProvider(MailboxSession session);

    /**
     * Call endRequest on {@link Mapper} instances
     */
    @Override
    public void endProcessingRequest(MailboxSession session) {
        if (session == null) {
            return;
        }
        MessageMapper messageMapper = (MessageMapper) session.getAttributes().get(MESSAGEMAPPER);
        MailboxMapper mailboxMapper = (MailboxMapper) session.getAttributes().get(MAILBOXMAPPER);
        SubscriptionMapper subscriptionMapper = (SubscriptionMapper) session.getAttributes().get(SUBSCRIPTIONMAPPER);
        if (messageMapper != null) {
            messageMapper.endRequest();
        }
        if (mailboxMapper != null) {
            mailboxMapper.endRequest();
        }
        if (subscriptionMapper != null) {
            subscriptionMapper.endRequest();
        }
    }

    @Override
    public void startProcessingRequest(MailboxSession session) {
        // Do nothing
        
    }

    
}
