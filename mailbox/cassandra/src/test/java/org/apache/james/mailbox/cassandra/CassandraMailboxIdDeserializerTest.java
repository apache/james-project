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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.store.mail.model.MailboxIdDeserialisationException;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

public class CassandraMailboxIdDeserializerTest {

    private static final String UUID_STRING = "5530370f-44c6-4647-990e-7768ce5131d4";
    private static final String MALFORMED_UUID_STRING = "xyz";
    private static final CassandraId CASSANDRA_ID = CassandraId.of(UUID.fromString(UUID_STRING));

    private CassandraMailboxIdDeserializer mailboxIdDeserializer;

    @Before
    public void setUp() {
        mailboxIdDeserializer = new CassandraMailboxIdDeserializer();
    }

    @Test
    public void deserializeShouldWork() throws Exception {
        assertThat(mailboxIdDeserializer.deserialize(UUID_STRING)).isEqualTo(CASSANDRA_ID);
    }

    @Test(expected = MailboxIdDeserialisationException.class)
    public void deserializeShouldThrowOnMalformedData() throws Exception {
        mailboxIdDeserializer.deserialize(MALFORMED_UUID_STRING);
    }

}
