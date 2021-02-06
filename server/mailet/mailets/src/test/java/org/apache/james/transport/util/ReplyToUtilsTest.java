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

class ReplyToUtilsTest {
    @Test
    void getReplyToShouldReturnAbsentWhenReplyToIsNull() throws Exception {
        ReplyToUtils testee = ReplyToUtils.from((MailAddress) null);

        FakeMail fakeMail = FakeMail.defaultFakeMail();

        Optional<MailAddress> replyTo = testee.getReplyTo(fakeMail);

        assertThat(replyTo).isEmpty();
    }

    @Test
    void getReplyToShouldReturnNullWhenReplyToEqualsToUnaltered() throws Exception {
        ReplyToUtils testee = ReplyToUtils.from(SpecialAddress.UNALTERED);

        FakeMail fakeMail = FakeMail.defaultFakeMail();

        Optional<MailAddress> replyTo = testee.getReplyTo(fakeMail);

        assertThat(replyTo).isEmpty();
    }

    @Test
    void getReplyToShouldReturnSenderWhenReplyToIsCommon() throws Exception {
        MailAddress mailAddress = new MailAddress("test", "james.org");
        ReplyToUtils testee = ReplyToUtils.from(mailAddress);

        MailAddress expectedMailAddress = new MailAddress("sender", "james.org");
        FakeMail fakeMail = FakeMail.builder()
                .name("name")
                .sender(expectedMailAddress)
                .build();

        Optional<MailAddress> replyTo = testee.getReplyTo(fakeMail);

        assertThat(replyTo).contains(expectedMailAddress);
    }
}
