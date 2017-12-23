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
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.mail.MessagingException;

import org.apache.mailet.MailetContext;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.assertj.core.data.MapEntry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MimeDecodingMailetTest {

    private static final String MAIL_ATTRIBUTE = "mime.attachments";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Logger logger;
    private MailetContext mailetContext;
    private MimeDecodingMailet testee;

    @Before
    public void setUp() throws Exception {
        testee = new MimeDecodingMailet();
        logger = mock(Logger.class);
        mailetContext = FakeMailContext.builder()
                .logger(logger)
                .build();
    }

    @Test
    public void initShouldThrowWhenNoAttributeParameter() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .build();
        expectedException.expect(MailetException.class);
        testee.init(mailetConfig);
    }

    @Test
    public void initShouldThrowWhenAttributeParameterIsEmpty() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty(MimeDecodingMailet.ATTRIBUTE_PARAMETER_NAME, "")
                .build();
        expectedException.expect(MailetException.class);
        testee.init(mailetConfig);
    }

    @Test
    public void serviceShouldNotThrowWhenAttributeContentIsNotAMap() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty(MimeDecodingMailet.ATTRIBUTE_PARAMETER_NAME, MAIL_ATTRIBUTE)
                .build();
        testee.init(mailetConfig);

        FakeMail mail = FakeMail.defaultFakeMail();
        mail.setAttribute(MAIL_ATTRIBUTE, ImmutableList.of());

        testee.service(mail);
    }

    @Test
    public void serviceShouldNotThrowWhenAttributeContentIsAMapOfWrongTypes() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty(MimeDecodingMailet.ATTRIBUTE_PARAMETER_NAME, MAIL_ATTRIBUTE)
                .build();
        testee.init(mailetConfig);

        FakeMail mail = FakeMail.defaultFakeMail();
        mail.setAttribute(MAIL_ATTRIBUTE, ImmutableMap.of("1", "2"));

        testee.service(mail);
    }

    @Test
    public void serviceShouldNotSetAttributeWhenNone() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty(MimeDecodingMailet.ATTRIBUTE_PARAMETER_NAME, MAIL_ATTRIBUTE)
                .build();
        testee.init(mailetConfig);

        FakeMail mail = FakeMail.defaultFakeMail();

        testee.service(mail);
        assertThat(mail.getAttribute(MAIL_ATTRIBUTE)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serviceShouldChangeAttributeWhenDefined() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty(MimeDecodingMailet.ATTRIBUTE_PARAMETER_NAME, MAIL_ATTRIBUTE)
                .build();
        testee.init(mailetConfig);

        FakeMail mail = FakeMail.defaultFakeMail();
        String text = "Attachment content";
        String content = "Content-Transfer-Encoding: 8bit\r\n"
                + "Content-Type: application/octet-stream; charset=utf-8\r\n\r\n"
                + text;
        String expectedKey = "mimePart1";
        mail.setAttribute(MAIL_ATTRIBUTE, ImmutableMap.of(expectedKey, content.getBytes(StandardCharsets.UTF_8)));

        byte[] expectedValue = text.getBytes(StandardCharsets.UTF_8);
        testee.service(mail);

        Map<String, byte[]> processedAttribute = (Map<String, byte[]>) mail.getAttribute(MAIL_ATTRIBUTE);
        assertThat(processedAttribute).containsExactly(MapEntry.entry(expectedKey, expectedValue));
    }
}
