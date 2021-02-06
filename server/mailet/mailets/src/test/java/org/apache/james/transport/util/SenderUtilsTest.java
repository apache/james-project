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
package org.apache.james.transport.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;

class SenderUtilsTest {
    @Test
    void getSenderShouldReturnAbsentWhenSenderIsAbsent() throws Exception {
        SenderUtils testee = SenderUtils.from(Optional.empty());

        FakeMail fakeMail = FakeMail.defaultFakeMail();

        Optional<MailAddress> sender = testee.getSender(fakeMail);

        assertThat(sender).isEmpty();
    }

    @Test
    void getSenderShouldReturnAbsentWhenSenderEqualsToUnaltered() throws Exception {
        SenderUtils testee = SenderUtils.from(Optional.of(SpecialAddress.UNALTERED));

        FakeMail fakeMail = FakeMail.defaultFakeMail();

        Optional<MailAddress> sender = testee.getSender(fakeMail);

        assertThat(sender).isEmpty();
    }

    @Test
    void getSenderShouldReturnAbsentWhenSenderEqualsToSender() throws Exception {
        SenderUtils testee = SenderUtils.from(Optional.of(SpecialAddress.SENDER));

        FakeMail fakeMail = FakeMail.defaultFakeMail();

        Optional<MailAddress> sender = testee.getSender(fakeMail);

        assertThat(sender).isEmpty();
    }

    @Test
    void getSenderShouldReturnSenderWhenSenderIsCommon() throws Exception {
        MailAddress expectedMailAddress = new MailAddress("sender", "james.org");
        SenderUtils testee = SenderUtils.from(Optional.of(expectedMailAddress));

        FakeMail fakeMail = FakeMail.defaultFakeMail();

        Optional<MailAddress> sender = testee.getSender(fakeMail);

        assertThat(sender).contains(expectedMailAddress);
    }
}
