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

package org.apache.james.mailbox.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class MailboxIdRegistrationKeyTest {
    private static final String ID = "42";

    private static final MailboxIdRegistrationKey.Factory FACTORY = new MailboxIdRegistrationKey.Factory(new TestId.Factory());

    private static final MailboxIdRegistrationKey MAILBOX_ID_REGISTRATION_KEY = new MailboxIdRegistrationKey((TestId.of(42)));

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(MailboxIdRegistrationKey.class)
            .verify();
    }

    @Test
    void asStringShouldReturnSerializedMailboxId() {
        assertThat(MAILBOX_ID_REGISTRATION_KEY.asString())
            .isEqualTo(ID);
    }

    @Test
    void fromStringShouldReturnCorrespondingRegistrationKey() {
        assertThat(FACTORY.fromString(ID))
            .isEqualTo(MAILBOX_ID_REGISTRATION_KEY);
    }

    @Test
    void fromStringShouldThrowOnInvalidValues() {
        assertThatThrownBy(() -> FACTORY.fromString("invalid"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}