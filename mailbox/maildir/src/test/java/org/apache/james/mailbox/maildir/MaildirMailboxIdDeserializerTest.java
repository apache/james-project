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

package org.apache.james.mailbox.maildir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.apache.james.mailbox.store.mail.model.MailboxIdDeserialisationException;
import org.apache.james.mailbox.store.mail.model.MailboxIdDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaildirMailboxIdDeserializerTest {

    static final String SERIALIZED_ID = "2f3a4fcc-ca64-36e3-9bcf-33e92dd93135";
    static final String MALFORMED_SERIALIZED_ID = "az";
    static final MaildirId MAILDIR_ID = MaildirId.of(UUID.fromString(SERIALIZED_ID));

    MailboxIdDeserializer mailboxIdDeserializer;

    @BeforeEach
    void setUp() {
        mailboxIdDeserializer = new MaildirMailboxIdDeserializer();
    }

    @Test
    void deserializeShouldWork() throws Exception {
        assertThat(mailboxIdDeserializer.deserialize(SERIALIZED_ID)).isEqualTo(MAILDIR_ID);
    }

    @Test
    void deserializeShouldThrowOnMalformedData() {
        assertThatThrownBy(() -> mailboxIdDeserializer.deserialize(MALFORMED_SERIALIZED_ID))
            .isInstanceOf(MailboxIdDeserialisationException.class);
    }

}
