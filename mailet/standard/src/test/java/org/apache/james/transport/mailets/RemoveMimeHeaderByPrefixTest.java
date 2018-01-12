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

import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.MessagingException;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.transport.mailets.utils.MimeMessageUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RemoveMimeHeaderByPrefixTest {

    private static final String PREFIX = "X-OPENPAAS-";
    private static final String HEADER_NAME_PREFIX_1 = "X-OPENPAAS-FEATURE-A";
    private static final String HEADER_NAME_PREFIX_2 = "X-OPENPAAS-FEATURE-B";
    private static final String HEADER_NAME_NO_PREFIX = "X-OTHER-BUSINESS";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private GenericMailet mailet;

    @Before
    public void setup() {
        mailet = new RemoveMimeHeaderByPrefix();
    }

    @Test
    public void getMailetInfoShouldReturnValue() {
        assertThat(mailet.getMailetInfo()).isEqualTo("RemoveMimeHeaderByPrefix Mailet");
    }

    @Test
    public void serviceShouldRemoveHeaderWhenPrefixed() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("prefix", PREFIX)
                .build();
        mailet.init(mailetConfig);

        Mail mail = FakeMail.fromMessage(MimeMessageBuilder.mimeMessageBuilder()
            .addHeader(HEADER_NAME_PREFIX_1, "true"));

        mailet.service(mail);

        assertThat(new MimeMessageUtils(mail.getMessage()).toHeaderList())
            .extracting("name")
            .doesNotContain(HEADER_NAME_PREFIX_1);
    }

    @Test
    public void serviceShouldRemoveAllPrefixedHeaders() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("prefix", PREFIX)
            .build();
        mailet.init(mailetConfig);

        Mail mail = FakeMail.fromMessage(MimeMessageBuilder.mimeMessageBuilder()
            .addHeader(HEADER_NAME_PREFIX_1, "true")
            .addHeader(HEADER_NAME_PREFIX_2, "true"));

        mailet.service(mail);

        assertThat(new MimeMessageUtils(mail.getMessage()).toHeaderList())
            .extracting("name")
            .doesNotContain(HEADER_NAME_PREFIX_1, HEADER_NAME_PREFIX_2);
    }

    @Test
    public void serviceShouldNotRemoveNonPrefixedHeaders() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("prefix", PREFIX)
            .build();
        mailet.init(mailetConfig);

        Mail mail = FakeMail.fromMessage(MimeMessageBuilder.mimeMessageBuilder()
            .addHeader(HEADER_NAME_PREFIX_1, "true")
            .addHeader(HEADER_NAME_NO_PREFIX, "true"));

        mailet.service(mail);

        assertThat(new MimeMessageUtils(mail.getMessage()).toHeaderList())
            .extracting("name")
            .contains(HEADER_NAME_NO_PREFIX)
            .doesNotContain(HEADER_NAME_PREFIX_1);
    }

    @Test
    public void exactMatchOfPrefixShouldBeAllowed() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("prefix", PREFIX)
            .build();
        mailet.init(mailetConfig);

        Mail mail = FakeMail.fromMessage(MimeMessageBuilder.mimeMessageBuilder()
            .addHeader(PREFIX, "true")
            .addHeader(HEADER_NAME_NO_PREFIX, "true"));

        mailet.service(mail);

        assertThat(new MimeMessageUtils(mail.getMessage()).toHeaderList())
            .extracting("name")
            .doesNotContain(PREFIX);
        assertThat(mail.getMessage().getHeader(PREFIX)).isNull();
    }


    @Test
    public void initShouldThrowWhenInvalidConfig() throws MessagingException {
        expectedException.expect(MessagingException.class);
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .build();
        mailet.init(mailetConfig);
    }

    @Test
    public void initShouldThrowWhenPrefixEmpty() throws MessagingException {
        expectedException.expect(MessagingException.class);
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty(RemoveMimeHeaderByPrefix.PREFIX, "")
            .build();
        mailet.init(mailetConfig);
    }
}
