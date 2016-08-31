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


package org.apache.james.transport.matchers;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.mailet.MailAddress;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.Before;
import org.junit.Test;

public class SenderIsTest {

    private final String SENDER_NAME = "test@james.apache.org";

    private Matcher matcher;
    private MailAddress recipient;

    @Before
    public void setUp() throws Exception {
        matcher = new SenderIs();
        matcher.init(new FakeMatcherConfig("SenderIs=" + SENDER_NAME, FakeMailContext.defaultContext()));
        recipient = new MailAddress("recipient@james.apache.org");
    }

    @Test
    public void shouldMatchWhenGoodSender() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .recipient(recipient)
            .sender(new MailAddress(SENDER_NAME))
            .build();

        assertThat(matcher.match(fakeMail)).containsExactly(recipient);
    }

    @Test
    public void shouldNotMatchWhenWrongSender() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .recipient(recipient)
            .sender(new MailAddress("other@james.apache.org"))
            .build();

        assertThat(matcher.match(fakeMail)).isNull();
    }

    @Test
    public void shouldNotMatchWhenNullSender() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .recipient(recipient)
            .build();

        assertThat(matcher.match(fakeMail)).isNull();
    }
}
