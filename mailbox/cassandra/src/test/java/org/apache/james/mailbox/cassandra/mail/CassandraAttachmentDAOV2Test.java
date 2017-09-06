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

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.cassandra.modules.CassandraBlobModule;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public class CassandraAttachmentDAOV2Test {
    public static final AttachmentId ATTACHMENT_ID = AttachmentId.from("id1");

    @ClassRule
    public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    private CassandraCluster cassandra;

    private CassandraAttachmentDAOV2 testee;

    @Before
    public void setUp() throws Exception {
        CassandraModuleComposite compositeModule = new CassandraModuleComposite(
            new CassandraAttachmentModule(),
            new CassandraBlobModule());

        cassandra = CassandraCluster.create(
            compositeModule,
            cassandraServer.getIp(),
            cassandraServer.getBindingPort());

        testee = new CassandraAttachmentDAOV2(cassandra.getConf(), new CassandraBlobsDAO(cassandra.getConf()));
    }

    @After
    public void tearDown() throws Exception {
        cassandra.close();
    }

    @Test
    public void getAttachmentShouldReturnEmptyWhenAbsent() {
        Optional<Attachment> attachment = testee.getAttachment(ATTACHMENT_ID).join();

        assertThat(attachment).isEmpty();
    }

    @Test
    public void getAttachmentShouldReturnAttachmentWhenStored() throws Exception {
        Attachment attachment = Attachment.builder()
            .attachmentId(ATTACHMENT_ID)
            .type("application/json")
            .bytes("{\"property\":`\"value\"}".getBytes(StandardCharsets.UTF_8))
            .build();
        testee.storeAttachment(attachment).join();

        Optional<Attachment> actual = testee.getAttachment(ATTACHMENT_ID).join();

        assertThat(actual).contains(attachment);
    }
}
