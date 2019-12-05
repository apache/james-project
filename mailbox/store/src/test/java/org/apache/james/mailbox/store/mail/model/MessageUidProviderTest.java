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
package org.apache.james.mailbox.store.mail.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.MessageUid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageUidProviderTest {

    MessageUidProvider testee;

    @BeforeEach
    void setup() {
        testee = new MessageUidProvider();
    }

    @Test
    void nextShouldReturn1WhenFirstCall() {
        MessageUid messageUid = testee.next();

        MessageUid expectedMessageUid = MessageUid.of(1);
        assertThat(messageUid).isEqualTo(expectedMessageUid);
    }

    @Test
    void nextShouldReturn2WhenSecondCall() {
        testee.next();
        MessageUid messageUid = testee.next();

        MessageUid expectedMessageUid = MessageUid.of(2);
        assertThat(messageUid).isEqualTo(expectedMessageUid);
    }

    @Test
    void nextShouldReturn3WhenThirdCall() {
        testee.next();
        testee.next();
        MessageUid messageUid = testee.next();

        MessageUid expectedMessageUid = MessageUid.of(3);
        assertThat(messageUid).isEqualTo(expectedMessageUid);
    }
}
