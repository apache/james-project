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
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapperFactory;

/**
 * Maintain mapper instances by {@link MailboxSession}. So only one mapper instance is used
 * in a {@link MailboxSession}
 */
public abstract class MailboxSessionMapperFactory implements RequestAware, MailboxMapperFactory, MessageMapperFactory, AttachmentMapperFactory, SubscriptionMapperFactory{

    protected final static String ATTACHMENTMAPPER = "ATTACHMENTMAPPER";
    protected final static String MESSAGEMAPPER ="MESSAGEMAPPER";
    protected final static String MAILBOXMAPPER ="MAILBOXMAPPER";
    protected final static String SUBSCRIPTIONMAPPER ="SUBSCRIPTIONMAPPER";
    
    
    
    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapperFactory#getMessageMapper(MailboxSession)
     */
    public MessageMapper getMessageMapper(MailboxSession session) throws MailboxException {
        MessageMapper mapper = (MessageMapper) session.getAttributes().get(MESSAGEMAPPER);
        if (mapper == null) {
            mapper = createMessageMapper(session);
            session.getAttributes().put(MESSAGEMAPPER, mapper);
        }
        return mapper;
    }

    public AttachmentMapper getAttachmentMapper(MailboxSession session) throws MailboxException {
        AttachmentMapper mapper = (AttachmentMapper) session.getAttributes().get(ATTACHMENTMAPPER);
        if (mapper == null) {
            mapper = createAttachmentMapper(session);
            session.getAttributes().put(ATTACHMENTMAPPER, mapper);
        }
        return mapper;
    }

    /**
     * Create a {@link MessageMapper} instance which will get reused during the whole {@link MailboxSession}
     * 
     * @param session
     * @return messageMapper
     * @throws MailboxException
     */
    public abstract MessageMapper createMessageMapper(MailboxSession session) throws MailboxException;

    public abstract AttachmentMapper createAttachmentMapper(MailboxSession session) throws MailboxException;


    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapperFactory#getMailboxMapper(MailboxSession)
     */
    public MailboxMapper getMailboxMapper(MailboxSession session) throws MailboxException {
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
     * @param session
     * @return mailboxMapper
     * @throws MailboxException
     */
    public abstract MailboxMapper createMailboxMapper(MailboxSession session) throws MailboxException;

    /**
     * Create a {@link SubscriptionMapper} instance or return the one which exists for the {@link MailboxSession} already
     * 
     * @param session
     * @return mapper
     */
    public SubscriptionMapper getSubscriptionMapper(MailboxSession session) throws SubscriptionException {
        SubscriptionMapper mapper = (SubscriptionMapper) session.getAttributes().get(SUBSCRIPTIONMAPPER);
        if (mapper == null) {
            mapper = createSubscriptionMapper(session);
            session.getAttributes().put(SUBSCRIPTIONMAPPER, mapper);
        }
        return mapper;
    }
    
    /**
     * Create a {@link SubscriptionMapper} instance which will get reused during the whole {@link MailboxSession}
     * @param session
     * @return subscriptionMapper
     * @throws SubscriptionException
     */
    public abstract SubscriptionMapper createSubscriptionMapper(MailboxSession session) throws SubscriptionException;

    /**
     * Call endRequest on {@link Mapper} instances
     * 
     * @param session
     */
    public void endProcessingRequest(MailboxSession session) {
        if (session == null) return;
        MessageMapper messageMapper = (MessageMapper) session.getAttributes().get(MESSAGEMAPPER);
        MailboxMapper mailboxMapper = (MailboxMapper) session.getAttributes().get(MAILBOXMAPPER);
        SubscriptionMapper subscriptionMapper = (SubscriptionMapper) session.getAttributes().get(SUBSCRIPTIONMAPPER);
        if (messageMapper != null)
            messageMapper.endRequest();
        if (mailboxMapper != null)
            mailboxMapper.endRequest();
        if (subscriptionMapper != null)
            subscriptionMapper.endRequest();        
    }

    /**
     * Do nothing
     * 
     */
    public void startProcessingRequest(MailboxSession session) {
        // Do nothing
        
    }

    
}
