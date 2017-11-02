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

package org.apache.james.mailbox.jpa.openjpa;


import javax.inject.Inject;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAMailboxManager;
import org.apache.james.mailbox.jpa.JPAMailboxSessionMapperFactory;
import org.apache.james.mailbox.jpa.mail.model.openjpa.EncryptDecryptHelper;
import org.apache.james.mailbox.jpa.openjpa.OpenJPAMessageManager.AdvancedFeature;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.DelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;

/**
 * OpenJPA implementation of MailboxManager
 *
 */
public class OpenJPAMailboxManager extends JPAMailboxManager {

    private final AdvancedFeature feature;

    public OpenJPAMailboxManager(JPAMailboxSessionMapperFactory mapperFactory,
                                 Authenticator authenticator,
                                 Authorizator authorizator,
                                 MailboxPathLocker locker,
                                 boolean useStreaming,
                                 MessageParser messageParser,
                                 MessageId.Factory messageIdFactory,
                                 StoreMailboxAnnotationManager annotationManager,
                                 DelegatingMailboxListener delegatingMailboxListener,
                                 MailboxEventDispatcher mailboxEventDispatcher,
                                 StoreRightManager storeRightManager) {
        super(mapperFactory, authenticator, authorizator, locker, messageParser,
            messageIdFactory, delegatingMailboxListener, mailboxEventDispatcher, annotationManager, storeRightManager);
        if (useStreaming) {
            feature = AdvancedFeature.Streaming;
        } else {
            feature = AdvancedFeature.None;
        }
    }

    public OpenJPAMailboxManager(JPAMailboxSessionMapperFactory mapperFactory,
                                 Authenticator authenticator,
                                 Authorizator authorizator,
                                 MailboxPathLocker locker,
                                 String encryptPass,
                                 MessageParser messageParser,
                                 MessageId.Factory messageIdFactory,
                                 StoreMailboxAnnotationManager annotationManager,
                                 DelegatingMailboxListener delegatingMailboxListener,
                                 MailboxEventDispatcher mailboxEventDispatcher,
                                 StoreRightManager storeRightManager) {
        super(mapperFactory, authenticator, authorizator, locker, messageParser,
            messageIdFactory, delegatingMailboxListener, mailboxEventDispatcher, annotationManager, storeRightManager);
        if (encryptPass != null) {
            EncryptDecryptHelper.init(encryptPass);
            feature = AdvancedFeature.Encryption;
        } else {
            feature = AdvancedFeature.None;
        }
    }
    
    @Inject
    public OpenJPAMailboxManager(JPAMailboxSessionMapperFactory mapperFactory,
                                 Authenticator authenticator,
                                 Authorizator authorizator,
                                 MessageParser messageParser,
                                 MessageId.Factory messageIdFactory,
                                 DelegatingMailboxListener delegatingMailboxListener,
                                 MailboxEventDispatcher mailboxEventDispatcher,
                                 StoreMailboxAnnotationManager annotationManager,
                                 StoreRightManager storeRightManager) {
        this(mapperFactory, authenticator, authorizator, new JVMMailboxPathLocker(), false,
            messageParser, messageIdFactory,  annotationManager, delegatingMailboxListener, mailboxEventDispatcher,
           storeRightManager);
    }

    @Override
    protected StoreMessageManager createMessageManager(Mailbox mailboxRow, MailboxSession session) throws MailboxException {
        return new OpenJPAMessageManager(getMapperFactory(),
            getMessageSearchIndex(),
            getEventDispatcher(),
            getLocker(),
            mailboxRow,
            feature,
            getQuotaManager(),
            getQuotaRootResolver(),
            getMessageParser(),
            getMessageIdFactory(),
            getBatchSizes(),
            getImmutableMailboxMessageFactory(),
            getStoreRightManager());
    }
}
