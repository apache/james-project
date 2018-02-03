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

import java.util.ArrayList;

import javax.mail.MessagingException;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

public class MailAttributesListToMimeHeadersTest {

    private static final String VALUE_1_1 = "test1.1";
    private static final String VALUE_1_2 = "test1.2";
    private static final String VALUE_2_1 = "test2.1";
    private static final String VALUE_2_2 = "test2.2";
    private static final ImmutableList<String> MAIL_ATTRIBUTE_VALUE1 = ImmutableList.of(VALUE_1_1, VALUE_1_2);
    private static final ImmutableList<String> MAIL_ATTRIBUTE_VALUE2 = ImmutableList.of(VALUE_2_1, VALUE_2_2);

    private static final String MAIL_ATTRIBUTE_NAME1 = "org.apache.james.test";
    private static final String MAIL_ATTRIBUTE_NAME2 = "org.apache.james.test2";
    private static final String HEADER_NAME1 = "JUNIT";
    private static final String HEADER_NAME2 = "JUNIT2";

    @Rule public ExpectedException expectedException = ExpectedException.none();

    private Mailet mailet;

    @Before
    public void setup() {
        mailet = new MailAttributesListToMimeHeaders();
    }

    @Test
    public void shouldThrowMessagingExceptionIfMappingIsNotGiven() throws MessagingException {
        expectedException.expect(MessagingException.class);

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .build();

        mailet.init(mailetConfig);
    }

    @Test
    public void shouldThrowMessagingExceptionIfMappingIsEmpty() throws MessagingException {
        expectedException.expect(MessagingException.class);

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("simplemmapping", "")
            .build();

        mailet.init(mailetConfig);
    }

    @Test
    public void shouldIgnoreAttributeOfMappingThatDoesNotExistOnTheMessage() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("simplemapping",
                MAIL_ATTRIBUTE_NAME1 + "; " + HEADER_NAME1 +
                    "," + MAIL_ATTRIBUTE_NAME2 + "; " + HEADER_NAME2 +
                    "," + "another.attribute" + "; " + "Another-Header")
            .build();

        mailet.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .mimeMessage(MailUtil.createMimeMessage())
            .attribute(MAIL_ATTRIBUTE_NAME1, MAIL_ATTRIBUTE_VALUE1)
            .attribute(MAIL_ATTRIBUTE_NAME2, MAIL_ATTRIBUTE_VALUE2)
            .build();

        mailet.service(mail);
        assertThat(mail.getMessage().getHeader("another.attribute")).isNull();
    }

    @Test
    public void shouldWorkWithMappingWithASingleBinding() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("simplemapping",
                MAIL_ATTRIBUTE_NAME1 + "; " + HEADER_NAME1)
            .build();

        mailet.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .mimeMessage(MailUtil.createMimeMessage())
            .attribute(MAIL_ATTRIBUTE_NAME1, MAIL_ATTRIBUTE_VALUE1)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER_NAME1))
            .containsExactly(VALUE_1_1, VALUE_1_2);
    }

    @Test
    public void shouldIgnoreNullValueInsideList() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("simplemapping",
                MAIL_ATTRIBUTE_NAME1 + "; " + HEADER_NAME1)
            .build();

        mailet.init(mailetConfig);

        ArrayList<String> listWithNull = new ArrayList<String>();
        listWithNull.add("1");
        listWithNull.add(null);
        listWithNull.add("2");
        FakeMail mail = FakeMail.builder()
            .mimeMessage(MailUtil.createMimeMessage())
            .attribute(MAIL_ATTRIBUTE_NAME1, listWithNull)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER_NAME1))
            .containsExactly("1", "2");
    }

    @Test
    public void shouldPutAttributesIntoHeadersWhenMappingDefined() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("simplemapping",
                        MAIL_ATTRIBUTE_NAME1 + "; " + HEADER_NAME1 +
                        "," + MAIL_ATTRIBUTE_NAME2 + "; " + HEADER_NAME2 +
                        "," + "another.attribute" + "; " + "Another-Header")
                .build();
        mailet.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .mimeMessage(MailUtil.createMimeMessage())
            .attribute(MAIL_ATTRIBUTE_NAME1, MAIL_ATTRIBUTE_VALUE1)
            .attribute(MAIL_ATTRIBUTE_NAME2, MAIL_ATTRIBUTE_VALUE2)
            .attribute("unmatched.attribute", "value")
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER_NAME1))
            .containsExactlyElementsOf(MAIL_ATTRIBUTE_VALUE1);

        assertThat(mail.getMessage().getHeader(HEADER_NAME2))
            .containsExactlyElementsOf(MAIL_ATTRIBUTE_VALUE2);
    }

    @Test
    public void shouldNotRemovePreviousAttributeValueWhenAttributeAlreadyPresent() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("simplemapping", MAIL_ATTRIBUTE_NAME1 + "; " + HEADER_NAME1)
                .build();
        mailet.init(mailetConfig);

        String firstValue = "first value";
        FakeMail mail = FakeMail.builder()
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(HEADER_NAME1, firstValue))
            .attribute(MAIL_ATTRIBUTE_NAME1, MAIL_ATTRIBUTE_VALUE1)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER_NAME1))
            .containsOnly(VALUE_1_1, VALUE_1_2, firstValue);
    }

    @Test
    public void shouldFilterAttributeOfWrongClass() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("simplemapping",
                MAIL_ATTRIBUTE_NAME1 + "; " + HEADER_NAME1 +
                    "," + MAIL_ATTRIBUTE_NAME2 + "; " + HEADER_NAME2)
            .build();
        mailet.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
            .attribute(MAIL_ATTRIBUTE_NAME1, 3L)
            .attribute(MAIL_ATTRIBUTE_NAME2, MAIL_ATTRIBUTE_VALUE2)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER_NAME1)).isNull();
        assertThat(mail.getMessage().getHeader(HEADER_NAME2))
            .containsExactlyElementsOf(MAIL_ATTRIBUTE_VALUE2);
    }


    @Test
    public void shouldFilterAttributeElementsOfWrongClass() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("simplemapping", MAIL_ATTRIBUTE_NAME1 + "; " + HEADER_NAME1)
            .build();
        mailet.init(mailetConfig);

        String value = "value";
        FakeMail mail = FakeMail.builder()
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
            .attribute(MAIL_ATTRIBUTE_NAME1, ImmutableList.of(3L, value))
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER_NAME1)).containsOnly(value);
    }


    @Test
    public void shouldThrowAtInitWhenNoSemicolumnInConfigurationEntry() throws MessagingException {
        expectedException.expect(IllegalArgumentException.class);

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("simplemapping", "invalidConfigEntry")
                .build();

        mailet.init(mailetConfig);
    }

    @Test
    public void shouldThrowAtInitWhenTwoSemicolumnsInConfigurationEntry() throws MessagingException {
        expectedException.expect(IllegalArgumentException.class);

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("simplemapping", "first;second;third")
                .build();

        mailet.init(mailetConfig);
    }

    @Test
    public void shouldThrowAtInitWhenNoConfigurationEntry() throws MessagingException {
        expectedException.expect(MessagingException.class);

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .build();

        mailet.init(mailetConfig);
    }
}
