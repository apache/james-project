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

import java.util.EnumSet;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.jcr.mail.model.JCRMailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.DelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;

/**
 * JCR implementation of a MailboxManager
 * 
 */
public class JCRMailboxManager extends StoreMailboxManager implements JCRImapConstants {

    public JCRMailboxManager(JCRMailboxSessionMapperFactory mapperFactory,
                             Authenticator authenticator,
                             Authorizator authorizator,
                             MailboxPathLocker locker,
                             MessageParser messageParser,
                             MessageId.Factory messageIdFactory,
                             MailboxEventDispatcher mailboxEventDispatcher,
                             DelegatingMailboxListener delegatingMailboxListener,
                             StoreMailboxAnnotationManager annotationManager,
                             StoreRightManager storeRightManager) {
        super(mapperFactory, authenticator, authorizator, locker, messageParser, messageIdFactory,
            annotationManager, mailboxEventDispatcher, delegatingMailboxListener, storeRightManager);
    }

    @Override
    public EnumSet<MailboxCapabilities> getSupportedMailboxCapabilities() {
        return EnumSet.of(MailboxCapabilities.Namespace);
    }
    
    @Override
    protected StoreMessageManager createMessageManager(Mailbox mailboxEntity, MailboxSession session) {
        return new JCRMessageManager(getMapperFactory(),
            getMessageSearchIndex(),
            getEventDispatcher(),
            getLocker(),
            (JCRMailbox) mailboxEntity,
            getQuotaManager(),
            getQuotaRootResolver(),
            getMessageParser(),
            getMessageIdFactory(),
            getBatchSizes(),
            getImmutableMailboxMessageFactory(),
            getStoreRightManager());
    }

    @Override
    protected Mailbox doCreateMailbox(MailboxPath path, MailboxSession session) {
        return new JCRMailbox(path, randomUidValidity());
    }

}
