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

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRestartRule;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.utils.GuiceUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.model.AttachmentMapperTest;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

public class CassandraAttachmentMapperTest extends AttachmentMapperTest {

    private static final CassandraModule MODULES = CassandraModule.aggregateModules(
        CassandraAttachmentModule.MODULE,
        CassandraBlobModule.MODULE);

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    @Rule
    public DockerCassandraRestartRule cassandraRestartRule = new DockerCassandraRestartRule();

    private CassandraCluster cassandra;

    @Override
    @Before
    public void setUp() throws MailboxException {
        cassandra = CassandraCluster.create(MODULES, cassandraServer.getHost());
        super.setUp();
    }
    
    @After
    public void tearDown() {
        cassandra.clearTables();
        cassandra.closeCluster();
    }

    @Override
    protected AttachmentMapper createAttachmentMapper() {
        return GuiceUtils.testInjector(cassandra)
            .getInstance(CassandraAttachmentMapper.class);
    }

    @Override
    protected MessageId generateMessageId() {
        return new CassandraMessageId.Factory().generate();
    }
}
