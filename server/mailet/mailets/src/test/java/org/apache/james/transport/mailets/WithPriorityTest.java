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
package org.apache.james.transport.mailets;

import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

public class WithPriorityTest {

    private WithPriority mailet;

    @Before
    public void setup() throws Exception {
        mailet = new WithPriority();
    }

    @Test
    public void getMailetInfoShouldReturnExpectedContent() {
        String expected = "With Priority Mailet";

        String actual = mailet.getMailetInfo();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void initShouldNotThrowWhenValidPriority() {
        MailetConfig mockedMailetConfig = FakeMailetConfig.builder()
            .mailetContext(FakeMailContext.defaultContext())
            .setProperty("priority", "7")
            .build();

        assertThatCode(() -> mailet.init(mockedMailetConfig))
            .doesNotThrowAnyException();
    }

    @Test
    public void initShouldThrowWhenInvalidPriority() {
        MailetConfig mockedMailetConfig = FakeMailetConfig.builder()
            .mailetContext(FakeMailContext.defaultContext())
            .setProperty("priority", "-1")
            .build();

        assertThatThrownBy(() -> mailet.init(mockedMailetConfig))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void initShouldThrowWhenPriorityIsNotANumber() {
        MailetConfig mockedMailetConfig = FakeMailetConfig.builder()
                .mailetContext(FakeMailContext.defaultContext())
                .setProperty("priority", "k")
                .build();

        assertThatThrownBy(() -> mailet.init(mockedMailetConfig))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    public void initShouldThrowWhenPriorityIsEmpty() {
        MailetConfig mockedMailetConfig = FakeMailetConfig.builder()
                .mailetContext(FakeMailContext.defaultContext())
                .setProperty("priority", "")
                .build();

        assertThatThrownBy(() -> mailet.init(mockedMailetConfig))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    public void initShouldThrowWhenNoPriority() {
        MailetConfig mockedMailetConfig = FakeMailetConfig.builder()
                .mailetContext(FakeMailContext.defaultContext())
                .build();

        assertThatThrownBy(() -> mailet.init(mockedMailetConfig))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void serviceShouldSetMailPriorityWhenNone() throws Exception {
        MailetConfig mockedMailetConfig = FakeMailetConfig.builder()
            .mailetContext(FakeMailContext.defaultContext())
            .setProperty("priority", "7")
            .build();

        mailet.init(mockedMailetConfig);
        Mail mail = FakeMail.builder().build();
        mailet.service(mail);

        assertThat(mail.getAttribute(MailPrioritySupport.MAIL_PRIORITY)).isEqualTo(7);
    }

    @Test
    public void serviceShouldSetMailPriorityWhenPriorityExists() throws Exception {
        MailetConfig mockedMailetConfig = FakeMailetConfig.builder()
            .mailetContext(FakeMailContext.defaultContext())
            .setProperty("priority", "7")
            .build();

        mailet.init(mockedMailetConfig);
        Mail mail = FakeMail.builder()
                .attribute(MailPrioritySupport.MAIL_PRIORITY, 5)
                .build();
        mailet.service(mail);

        assertThat(mail.getAttribute(MailPrioritySupport.MAIL_PRIORITY)).isEqualTo(7);
    }
}
