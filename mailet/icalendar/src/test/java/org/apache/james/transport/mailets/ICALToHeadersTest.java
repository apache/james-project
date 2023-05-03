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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import javax.mail.MessagingException;

import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;

class ICALToHeadersTest {

    ICALToHeader testee;

    @BeforeEach
    void setUp() {
        testee = new ICALToHeader();
    }

    @Test
    void getMailetInfoShouldReturnExpectedValue() {
        assertThat(testee.getMailetInfo()).isEqualTo("ICALToHeader Mailet");
    }

    @Test
    void initShouldSetAttributeWhenAbsent() throws Exception {
        testee.init(FakeMailetConfig.builder()
            .mailetName("ICALToHeader")
            .build());

        assertThat(testee.getAttribute().asString()).isEqualTo(ICALToHeader.ATTRIBUTE_DEFAULT_NAME);
    }

    @Test
    void initShouldSetAttributeWhenPresent() throws Exception {
        String attribute = "attribute";
        testee.init(FakeMailetConfig.builder()
            .mailetName("ICALToHeader")
            .setProperty(ICALToHeader.ATTRIBUTE_PROPERTY, attribute)
            .build());

        assertThat(testee.getAttribute().asString()).isEqualTo(attribute);
    }

    @Test
    void initShouldThrowOnEmptyAttribute() {
        assertThatThrownBy(() -> testee.init(FakeMailetConfig.builder()
                .mailetName("ICALToHeader")
                .setProperty(ICALToHeader.ATTRIBUTE_PROPERTY, "")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void serviceShouldNotModifyMailsWithoutIcalAttribute() throws Exception {
        testee.init(FakeMailetConfig.builder().build());
        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .build();

        testee.service(mail);

        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_UID_HEADER)).isNull();
    }

    @Test
    void serviceShouldNotFailOnMailsWithWrongAttributeType() throws Exception {
        testee.init(FakeMailetConfig.builder().build());
        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .attribute(new Attribute(ICALToHeader.ATTRIBUTE_DEFAULT, AttributeValue.of("This is the wrong type")))
            .build();

        testee.service(mail);

        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_UID_HEADER)).isNull();
    }

    @Test
    void serviceShouldNotFailOnMailsWithWrongParametrizedAttribute() throws Exception {
        ImmutableMap<String, AttributeValue<?>> wrongParametrizedMap = ImmutableMap.<String, AttributeValue<?>>builder()
            .put("key", AttributeValue.of("value"))
            .build();

        testee.init(FakeMailetConfig.builder().build());
        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .attribute(makeAttribute(wrongParametrizedMap))
            .build();

        testee.service(mail);

        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_UID_HEADER)).isNull();
    }

    @Test
    void serviceShouldWriteSingleICalendarToHeaders() throws Exception {
        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"));
        Map<String, AttributeValue<?>> icals = ImmutableMap.<String, AttributeValue<?>>builder()
            .put("key", AttributeValue.ofUnserializable(calendar))
            .build();

        testee.init(FakeMailetConfig.builder().build());
        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .attribute(makeAttribute(icals))
            .build();

        testee.service(mail);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            softly.assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_METHOD_HEADER)).containsOnly("REQUEST");
            softly.assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_UID_HEADER))
                .containsOnly("f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc");
            softly.assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_DTSTAMP_HEADER)).containsOnly("20170106T115036Z");
            softly.assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_RECURRENCE_ID_HEADER)).isNull();
            softly.assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_SEQUENCE_HEADER)).containsOnly("0");
        }));

    }

    @Test
    void serviceShouldNotWriteHeaderWhenPropertyIsAbsent() throws Exception {
        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting_without_dtstamp.ics"));
        Map<String, AttributeValue<?>> icals = ImmutableMap.<String, AttributeValue<?>>builder()
            .put("key", AttributeValue.ofUnserializable(calendar))
            .build();

        testee.init(FakeMailetConfig.builder().build());
        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .attribute(makeAttribute(icals))
            .build();

        testee.service(mail);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            softly.assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_METHOD_HEADER)).containsOnly("REQUEST");
            softly.assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_UID_HEADER))
                .containsOnly("f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc");
            softly.assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_DTSTAMP_HEADER)).isNull();
            softly.assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_RECURRENCE_ID_HEADER)).isNull();
            softly.assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_SEQUENCE_HEADER)).containsOnly("0");
        }));
    }

    @Test
    void serviceShouldWriteOnlyOneICalendarToHeaders() throws Exception {
        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"));
        Calendar calendar2 = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting_2.ics"));
        Map<String, AttributeValue<?>> icals = ImmutableMap.<String, AttributeValue<?>>builder()
            .put("key", AttributeValue.ofUnserializable(calendar))
            .put("key2", AttributeValue.ofUnserializable(calendar2))
            .build();

        testee.init(FakeMailetConfig.builder().build());
        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .attribute(makeAttribute(icals))
            .build();

        testee.service(mail);

        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_UID_HEADER)).hasSize(1);
    }

    @Test
    void serviceShouldNotFailOnEmptyMaps() throws Exception {
        Map<String, AttributeValue<?>> icals = ImmutableMap.<String, AttributeValue<?>>builder()
            .build();

        testee.init(FakeMailetConfig.builder().build());
        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .attribute(makeAttribute(icals))
            .build();

        testee.service(mail);

        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_UID_HEADER)).isNull();
    }

    private Attribute makeAttribute(Map<String, AttributeValue<?>> icals) {
        return new Attribute(ICALToHeader.ATTRIBUTE_DEFAULT, AttributeValue.of(icals));
    }
}
