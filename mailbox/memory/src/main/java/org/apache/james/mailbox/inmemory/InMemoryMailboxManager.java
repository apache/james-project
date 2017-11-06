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

import java.util.EnumSet;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.DelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.search.MessageSearchIndex;

public class InMemoryMailboxManager extends StoreMailboxManager {

    @Inject
    public InMemoryMailboxManager(MailboxSessionMapperFactory mailboxSessionMapperFactory, Authenticator authenticator, Authorizator authorizator,
                                  MailboxPathLocker locker, MessageParser messageParser, MessageId.Factory messageIdFactory, MailboxEventDispatcher dispatcher,
                                  DelegatingMailboxListener delegatingMailboxListener,
                                  StoreMailboxAnnotationManager annotationManager,
                                  StoreRightManager storeRightManager) {
        super(mailboxSessionMapperFactory, authenticator, authorizator, locker, messageParser, messageIdFactory,
            annotationManager, dispatcher,
            delegatingMailboxListener, storeRightManager);
    }

    @Override
    @Inject
    public void setMessageSearchIndex(MessageSearchIndex index) {
        super.setMessageSearchIndex(index);
    }

    @Override
    public EnumSet<MailboxCapabilities> getSupportedMailboxCapabilities() {
        return EnumSet.of(MailboxCapabilities.Move,
            MailboxCapabilities.UserFlag,
            MailboxCapabilities.Namespace,
            MailboxCapabilities.Annotation,
            MailboxCapabilities.ACL);
    }
    
    @Override
    public EnumSet<MessageCapabilities> getSupportedMessageCapabilities() {
        return EnumSet.of(MessageCapabilities.Attachment, MessageCapabilities.UniqueID);
    }

    @Override
    protected StoreMessageManager createMessageManager(Mailbox mailbox, MailboxSession session) throws MailboxException {
        return new InMemoryMessageManager(getMapperFactory(),
            getMessageSearchIndex(),
            getEventDispatcher(),
            getLocker(),
            mailbox,
            getQuotaManager(),
            getQuotaRootResolver(),
            getMessageParser(),
            getMessageIdFactory(),
            getBatchSizes(),
            getImmutableMailboxMessageFactory(),
            getStoreRightManager());
    }
}
