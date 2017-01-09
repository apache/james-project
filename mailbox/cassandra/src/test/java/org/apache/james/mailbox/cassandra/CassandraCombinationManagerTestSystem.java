/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.cassandra;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.store.CombinationManagerTestSystem;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.Mailbox;

public class CassandraCombinationManagerTestSystem extends CombinationManagerTestSystem {
    private final CassandraMessageId.Factory messageIdFactory;
    private final CassandraMailboxSessionMapperFactory mapperFactory;
    private final CassandraMailboxManager cassandraMailboxManager;

    public static CombinationManagerTestSystem createTestingData(QuotaManager quotaManager, MailboxEventDispatcher dispatcher) throws Exception {
        CassandraMailboxSessionMapperFactory mapperFactory = CassandraTestSystemFixture.createMapperFactory();

        return new CassandraCombinationManagerTestSystem(CassandraTestSystemFixture.createMessageIdManager(mapperFactory, quotaManager, dispatcher),
            new CassandraMessageId.Factory(),
            mapperFactory,
            CassandraTestSystemFixture.createMailboxManager(mapperFactory));
    }

    private CassandraCombinationManagerTestSystem(MessageIdManager messageIdManager, CassandraMessageId.Factory messageIdFactory, CassandraMailboxSessionMapperFactory mapperFactory, MailboxManager cassandraMailboxManager) {
        super(cassandraMailboxManager, messageIdManager);
        this.messageIdFactory = messageIdFactory;
        this.mapperFactory = mapperFactory;
        this.cassandraMailboxManager = (CassandraMailboxManager)cassandraMailboxManager;
    }

    @Override
    public Mailbox createMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        cassandraMailboxManager.createMailbox(mailboxPath, session);
        return mapperFactory.getMailboxMapper(session).findMailboxByPath(mailboxPath);
    }

    @Override
    public MessageManager createMessageManager(Mailbox mailbox, MailboxSession session) throws MailboxException {
        return cassandraMailboxManager.createMessageManager(mailbox, session);
    }

    @Override
    public void clean() {
        CassandraTestSystemFixture.clean();
    }
}
