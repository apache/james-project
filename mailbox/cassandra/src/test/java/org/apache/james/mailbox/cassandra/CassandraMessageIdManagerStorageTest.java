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

package org.apache.james.mailbox.cassandra;

import static org.mockito.Mockito.mock;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.store.AbstractMessageIdManagerStorageTest;
import org.apache.james.mailbox.store.MessageIdManagerTestSystem;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class CassandraMessageIdManagerStorageTest extends AbstractMessageIdManagerStorageTest {

    @BeforeClass
    public static void init() {
        CassandraMessageIdManagerTestSystem.init();
    }

    @AfterClass
    public static void close() {
        CassandraMessageIdManagerTestSystem.stop();
    }

    @Override
    protected MessageIdManagerTestSystem createTestingData() throws Exception {
        return CassandraMessageIdManagerTestSystem.createTestingData(new NoQuotaManager(), MailboxEventDispatcher.ofListener(mock(MailboxListener.class)));
    }
}
