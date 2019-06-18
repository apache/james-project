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
package org.apache.james.mailbox.store.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * Test for UID provider.
 */
public class ZooUidProviderTest {

    public static class LongId implements MailboxId, Serializable {

        public final Long id;

        public LongId(long id) {
            this.id = id;
        }

        @Override
        public String serialize() {
            return String.valueOf(id);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            LongId other = (LongId) obj;
            if (id == null) {
                if (other.id != null) {
                    return false;
                }
            } else if (!id.equals(other.id)) {
                return false;
            }
            return true;
        }

    }

    public static class UUIDId implements MailboxId, Serializable {

        private final UUID id;

        public static UUIDId of(UUID id) {
            return new UUIDId(id);
        }

        public UUIDId(UUID id) {
            this.id = id;
        }

        @Override
        public String serialize() {
            return id.toString();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            UUIDId other = (UUIDId) obj;
            if (id == null) {
                if (other.id != null) {
                    return false;
                }
            } else if (!id.equals(other.id)) {
                return false;
            }
            return true;
        }

    }

    private static TestingServer testServer;
    private static final int ZOO_TEST_PORT = 3123;
    private final RetryPolicy retryPolicy = new RetryOneTime(1);
    private CuratorFramework client;
    private ZooUidProvider uuidProvider;
    private ZooUidProvider longProvider;
    private Mailbox mailboxUUID;
    private Mailbox mailboxLong;
    private UUID randomUUID = UUID.randomUUID();

    @Before
    public void setUp() throws Exception {
        testServer = new TestingServer(ZOO_TEST_PORT);
        client = CuratorFrameworkFactory.builder().connectString("localhost:" + ZOO_TEST_PORT)
                .retryPolicy(retryPolicy)
                .namespace("JAMES").build();
        client.start();
        uuidProvider = new ZooUidProvider(client, retryPolicy);
        longProvider = new ZooUidProvider(client, retryPolicy);
        MailboxPath path1 = new MailboxPath("namespacetest", "namespaceuser", "UUID");
        MailboxPath path2 = new MailboxPath("namespacetest", "namespaceuser", "Long");
        mailboxUUID = new Mailbox(path1, 1L);
        mailboxUUID.setMailboxId(UUIDId.of(randomUUID));
        mailboxLong = new Mailbox(path2, 2L);
        mailboxLong.setMailboxId(new LongId(123L));
    }

    @After
    public void tearDown() throws Exception {
        client.close();
        testServer.close();
    }

    @Test
    public void testNextUid() throws Exception {
        MessageUid result = uuidProvider.nextUid(null, mailboxUUID);
        assertThat(result.asLong()).describedAs("Next UID is 1").isEqualTo(1);
        result = longProvider.nextUid(null, mailboxLong);
        assertThat(result.asLong()).describedAs("Next UID is 1").isEqualTo(1);
    }

    @Test
    public void testLastUid() throws Exception {
        Optional<MessageUid> result = uuidProvider.lastUid(null, mailboxUUID);
        assertThat(result).describedAs("Next UID is empty").isEqualTo(Optional.empty());
        MessageUid nextResult = uuidProvider.nextUid(null, mailboxUUID);
        assertThat(nextResult.asLong()).describedAs("Next UID is 1").isEqualTo(1);
    }

    @Test
    public void testLongLastUid() throws Exception {
        Optional<MessageUid> result = longProvider.lastUid(null, mailboxLong);
        assertThat(result).describedAs("Next UID is empty").isEqualTo(Optional.empty());
        MessageUid nextResult = longProvider.nextUid(null, mailboxLong);
        assertThat(nextResult.asLong()).describedAs("Next UID is 1").isEqualTo(1);
    }
}
