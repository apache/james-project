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

import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import org.apache.mailet.base.test.MimeMessageBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MailAttributesToMimeHeadersTest {

    @Rule public ExpectedException expectedException = ExpectedException.none();
    
    private Mailet mailet;

    private final String HEADER_NAME1 = "JUNIT";
    private final String HEADER_NAME2 = "JUNIT2";

    private final String MAIL_ATTRIBUTE_VALUE1 = "test1";
    private final String MAIL_ATTRIBUTE_VALUE2 = "test2";

    private final String MAIL_ATTRIBUTE_NAME1 = "org.apache.james.test";
    private final String MAIL_ATTRIBUTE_NAME2 = "org.apache.james.test2";

    @Before
    public void setup() {
        mailet = new MailAttributesToMimeHeaders();
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
        
        FakeMail mockedMail = MailUtil.createMockMail2Recipients(MailUtil.createMimeMessage());
        mockedMail.setAttribute(MAIL_ATTRIBUTE_NAME1, MAIL_ATTRIBUTE_VALUE1);
        mockedMail.setAttribute(MAIL_ATTRIBUTE_NAME2, MAIL_ATTRIBUTE_VALUE2);
        mockedMail.setAttribute("unmatched.attribute", "value");

        mailet.service(mockedMail);

        assertThat(mockedMail.getMessage().getHeader(HEADER_NAME1)).containsExactly(MAIL_ATTRIBUTE_VALUE1);
        assertThat(mockedMail.getMessage().getHeader(HEADER_NAME2)).containsExactly(MAIL_ATTRIBUTE_VALUE2);
    }

    @Test
    public void shouldAddAttributeIntoHeadersWhenHeaderAlreadyPresent() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("simplemapping", MAIL_ATTRIBUTE_NAME1 + "; " + HEADER_NAME1)
                .build();
        mailet.init(mailetConfig);

        FakeMail mockedMail = MailUtil.createMockMail2Recipients(MimeMessageBuilder.mimeMessageBuilder()
            .addHeader(HEADER_NAME1, "first value")
            .build());
        mockedMail.setAttribute(MAIL_ATTRIBUTE_NAME1, MAIL_ATTRIBUTE_VALUE1);
        
        mailet.service(mockedMail);

        assertThat(mockedMail.getMessage().getHeader(HEADER_NAME1)).containsExactly("first value", MAIL_ATTRIBUTE_VALUE1);
    }

    
    @Test
    public void shouldThrowAtInitWhenNoSemicolumnInConfigurationEntry() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("simplemapping", "invalidConfigEntry")
                .build();
        expectedException.expect(IllegalArgumentException.class);
        mailet.init(mailetConfig);
    }

    @Test
    public void shouldThrowAtInitWhenTwoSemicolumnsInConfigurationEntry() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("simplemapping", "first;second;third")
                .build();
        expectedException.expect(IllegalArgumentException.class);
        mailet.init(mailetConfig);
    }

    @Test
    public void shouldThrowAtInitWhenNoConfigurationEntry() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .build();
        expectedException.expect(IllegalArgumentException.class);
        mailet.init(mailetConfig);
    }
}
