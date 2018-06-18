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
package org.apache.james.mailbox.hbase;

import java.util.EnumSet;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.hbase.mail.HBaseMailboxMapper;
import org.apache.james.mailbox.hbase.mail.model.HBaseMailbox;
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
import org.apache.james.mailbox.store.transaction.Mapper;

/**
 * HBase implementation of {@link StoreMailboxManager}
 * 
 */
public class HBaseMailboxManager extends StoreMailboxManager {

    public static final EnumSet<MailboxCapabilities> MAILBOX_CAPABILITIES = EnumSet.of(MailboxCapabilities.Namespace);

    public HBaseMailboxManager(HBaseMailboxSessionMapperFactory mapperFactory,
                               Authenticator authenticator,
                               Authorizator authorizator,
                               MailboxPathLocker locker,
                               MessageParser messageParser,
                               MessageId.Factory messageIdFactory,
                               MailboxEventDispatcher dispatcher,
                               DelegatingMailboxListener delegatingMailboxListener,
                               StoreMailboxAnnotationManager annotationManager,
                               StoreRightManager storeRightManager) {
        super(mapperFactory, authenticator, authorizator, locker, messageParser, messageIdFactory,
            annotationManager, dispatcher, delegatingMailboxListener, storeRightManager);
    }

    @Override
    protected Mailbox doCreateMailbox(MailboxPath mailboxPath, MailboxSession session) {
        return new HBaseMailbox(mailboxPath, randomUidValidity());
    }

    /**
     * Delete all mailboxes 
     * 
     * @param mailboxSession
     * @throws MailboxException
     */
    public void deleteEverything(MailboxSession mailboxSession) throws MailboxException {

        final HBaseMailboxMapper mapper = (HBaseMailboxMapper) getMapperFactory().getMailboxMapper(mailboxSession);

        mapper.execute(Mapper.toTransaction(mapper::deleteAllMemberships));
        mapper.execute(Mapper.toTransaction(mapper::deleteAllMailboxes));
    }

    @Override
    public EnumSet<MailboxCapabilities> getSupportedMailboxCapabilities() {
        return MAILBOX_CAPABILITIES;
    }

    @Override
    protected StoreMessageManager createMessageManager(Mailbox mailboxRow, MailboxSession session) throws MailboxException {
        return new HBaseMessageManager(getMapperFactory(),
            getMessageSearchIndex(),
            getEventDispatcher(),
            getLocker(),
            mailboxRow,
            getQuotaManager(),
            getQuotaRootResolver(),
            getMessageParser(),
            getMessageIdFactory(),
            getBatchSizes(),
            getImmutableMailboxMessageFactory(),
            getStoreRightManager());
    }
}
