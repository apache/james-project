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

package org.apache.james.mailbox.cassandra.event.distributed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraRegistrationModule;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.publisher.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMailboxPathRegistrerMapperTest {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraRegistrationModule.MODULE);

    private static final MailboxPath MAILBOX_PATH = new MailboxPath("namespace", "user", "name");
    private static final MailboxPath MAILBOX_PATH_2 = new MailboxPath("namespace2", "user2", "name2");
    private static final Topic TOPIC = new Topic("topic");
    private static final int CASSANDRA_TIME_OUT_IN_S = 100;
    private static final Topic TOPIC_2 = new Topic("topic2");

    private CassandraMailboxPathRegisterMapper mapper;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        mapper = new CassandraMailboxPathRegisterMapper(cassandra.getConf(),
            cassandra.getTypesProvider(),
            CassandraUtils.WITH_DEFAULT_CONFIGURATION,
            CASSANDRA_TIME_OUT_IN_S);
    }

    @Test
    void getTopicsShouldReturnEmptyResultByDefault() {
        assertThat(mapper.getTopics(MAILBOX_PATH)).isEmpty();
    }

    @Test
    void doRegisterShouldWork() {
        mapper.doRegister(MAILBOX_PATH, TOPIC);
        assertThat(mapper.getTopics(MAILBOX_PATH)).containsOnly(TOPIC);
    }

    @Test
    void doRegisterShouldBeMailboxPathSpecific() {
        mapper.doRegister(MAILBOX_PATH, TOPIC);
        assertThat(mapper.getTopics(MAILBOX_PATH_2)).isEmpty();
    }

    @Test
    void doRegisterShouldAllowMultipleTopics() {
        mapper.doRegister(MAILBOX_PATH, TOPIC);
        mapper.doRegister(MAILBOX_PATH, TOPIC_2);
        assertThat(mapper.getTopics(MAILBOX_PATH)).containsOnly(TOPIC, TOPIC_2);
    }

    @Test
    void doUnRegisterShouldWork() {
        mapper.doRegister(MAILBOX_PATH, TOPIC);
        mapper.doUnRegister(MAILBOX_PATH, TOPIC);
        assertThat(mapper.getTopics(MAILBOX_PATH)).isEmpty();
    }

    @Test
    void doUnregisterShouldBeMailboxSpecific() {
        mapper.doRegister(MAILBOX_PATH, TOPIC);
        mapper.doUnRegister(MAILBOX_PATH_2, TOPIC);
        assertThat(mapper.getTopics(MAILBOX_PATH)).containsOnly(TOPIC);
    }

    @Test
    void doUnregisterShouldBeTopicSpecific() {
        mapper.doRegister(MAILBOX_PATH, TOPIC);
        mapper.doUnRegister(MAILBOX_PATH, TOPIC_2);
        assertThat(mapper.getTopics(MAILBOX_PATH)).containsOnly(TOPIC);
    }

    @Test
    void entriesShouldExpire(CassandraCluster cassandra) throws Exception {
        int verySmallTimeoutInSecond = 1;
        mapper = new CassandraMailboxPathRegisterMapper(cassandra.getConf(),
            cassandra.getTypesProvider(),
            CassandraUtils.WITH_DEFAULT_CONFIGURATION,
            verySmallTimeoutInSecond);
        mapper.doRegister(MAILBOX_PATH, TOPIC);
        Thread.sleep(2 * TimeUnit.SECONDS.toMillis(verySmallTimeoutInSecond));
        assertThat(mapper.getTopics(MAILBOX_PATH)).isEmpty();
    }

}
