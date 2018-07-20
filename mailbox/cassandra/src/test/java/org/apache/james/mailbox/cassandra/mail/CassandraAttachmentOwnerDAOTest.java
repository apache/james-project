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

package org.apache.james.mailbox.cassandra.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.store.mail.model.Username;
import org.apache.james.util.FluentFutureStream;
import org.apache.james.util.streams.JamesCollectors;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public class CassandraAttachmentOwnerDAOTest {
    public static final AttachmentId ATTACHMENT_ID = AttachmentId.from("id1");
    public static final Username OWNER_1 = Username.fromRawValue("owner1");
    public static final Username OWNER_2 = Username.fromRawValue("owner2");

    @ClassRule
    public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    private CassandraCluster cassandra;

    private CassandraAttachmentOwnerDAO testee;

    @Before
    public void setUp() throws Exception {
        cassandra = CassandraCluster.create(new CassandraAttachmentModule(),
            cassandraServer.getIp(), cassandraServer.getBindingPort());
        testee = new CassandraAttachmentOwnerDAO(cassandra.getConf(),
            CassandraUtils.WITH_DEFAULT_CONFIGURATION);
    }

    @After
    public void tearDown() {
        cassandra.close();
    }

    @Test
    public void retrieveOwnersShouldReturnEmptyByDefault() {
        assertThat(testee.retrieveOwners(ATTACHMENT_ID).join())
            .isEmpty();
    }

    @Test
    public void retrieveOwnersShouldReturnAddedOwner() {
        testee.addOwner(ATTACHMENT_ID, OWNER_1).join();

        assertThat(testee.retrieveOwners(ATTACHMENT_ID).join())
            .containsOnly(OWNER_1);
    }

    @Test
    public void retrieveOwnersShouldReturnAddedOwners() {
        testee.addOwner(ATTACHMENT_ID, OWNER_1).join();
        testee.addOwner(ATTACHMENT_ID, OWNER_2).join();

        assertThat(testee.retrieveOwners(ATTACHMENT_ID).join())
            .containsOnly(OWNER_1, OWNER_2);
    }

    @Test
    public void retrieveOwnersShouldNotThrowWhenMoreReferencesThanPaging() {
        int referenceCountExceedingPaging = 5050;

        IntStream.range(0, referenceCountExceedingPaging)
            .boxed()
            .collect(JamesCollectors.chunker(128))
            .forEach(chunk -> FluentFutureStream.of(
                chunk.stream()
                    .map(i -> testee.addOwner(ATTACHMENT_ID, Username.fromRawValue("owner" + i))))
                .join());

        assertThat(testee.retrieveOwners(ATTACHMENT_ID).join())
            .hasSize(referenceCountExceedingPaging);
    }
}