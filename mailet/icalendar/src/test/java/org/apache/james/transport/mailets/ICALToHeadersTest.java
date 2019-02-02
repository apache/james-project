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

import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableMap;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;

public class ICALToHeadersTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private ICALToHeader testee;

    @Before
    public void setUp() {
        testee = new ICALToHeader();
    }

    @Test
    public void getMailetInfoShouldReturnExpectedValue() {
        assertThat(testee.getMailetInfo()).isEqualTo("ICALToHeader Mailet");
    }

    @Test
    public void initShouldSetAttributeWhenAbsent() throws Exception {
        testee.init(FakeMailetConfig.builder()
            .mailetName("ICALToHeader")
            .build());

        assertThat(testee.getAttribute()).isEqualTo(ICALToHeader.ATTRIBUTE_DEFAULT_NAME);
    }

    @Test
    public void initShouldSetAttributeWhenPresent() throws Exception {
        String attribute = "attribute";
        testee.init(FakeMailetConfig.builder()
            .mailetName("ICALToHeader")
            .setProperty(ICALToHeader.ATTRIBUTE_PROPERTY, attribute)
            .build());

        assertThat(testee.getAttribute()).isEqualTo(attribute);
    }

    @Test
    public void initShouldThrowOnEmptyAttribute() throws Exception {
        expectedException.expect(MessagingException.class);

        testee.init(FakeMailetConfig.builder()
            .mailetName("ICALToHeader")
            .setProperty(ICALToHeader.ATTRIBUTE_PROPERTY, "")
            .build());
    }

    @Test
    public void serviceShouldNotModifyMailsWithoutIcalAttribute() throws Exception {
        testee.init(FakeMailetConfig.builder().build());
        Mail mail = FakeMail.builder()
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .build();

        testee.service(mail);

        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_UID_HEADER)).isNull();
    }

    @Test
    public void serviceShouldNotFailOnMailsWithWrongAttributeType() throws Exception {
        testee.init(FakeMailetConfig.builder().build());
        Mail mail = FakeMail.builder()
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .attribute(ICALToHeader.ATTRIBUTE_DEFAULT_NAME, "This is the wrong type")
            .build();

        testee.service(mail);

        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_UID_HEADER)).isNull();
    }

    @Test
    public void serviceShouldNotFailOnMailsWithWrongParametrizedAttribute() throws Exception {
        ImmutableMap<String, String> wrongParametrizedMap = ImmutableMap.<String, String>builder()
            .put("key", "value")
            .build();

        testee.init(FakeMailetConfig.builder().build());
        Mail mail = FakeMail.builder()
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .attribute(ICALToHeader.ATTRIBUTE_DEFAULT_NAME, wrongParametrizedMap)
            .build();

        testee.service(mail);

        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_UID_HEADER)).isNull();
    }

    @Test
    public void serviceShouldWriteSingleICalendarToHeaders() throws Exception {
        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"));
        ImmutableMap<String, Calendar> icals = ImmutableMap.<String, Calendar>builder()
            .put("key", calendar)
            .build();

        testee.init(FakeMailetConfig.builder().build());
        Mail mail = FakeMail.builder()
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .attribute(ICALToHeader.ATTRIBUTE_DEFAULT_NAME, icals)
            .build();

        testee.service(mail);

        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_METHOD_HEADER)).containsOnly("REQUEST");
        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_UID_HEADER))
            .containsOnly("f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc");
        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_DTSTAMP_HEADER)).containsOnly("20170106T115036Z");
        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_RECURRENCE_ID_HEADER)).isNull();
        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_SEQUENCE_HEADER)).containsOnly("0");
    }

    @Test
    public void serviceShouldNotWriteHeaderWhenPropertyIsAbsent() throws Exception {
        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting_without_dtstamp.ics"));
        ImmutableMap<String, Calendar> icals = ImmutableMap.<String, Calendar>builder()
            .put("key", calendar)
            .build();

        testee.init(FakeMailetConfig.builder().build());
        Mail mail = FakeMail.builder()
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .attribute(ICALToHeader.ATTRIBUTE_DEFAULT_NAME, icals)
            .build();

        testee.service(mail);

        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_METHOD_HEADER)).containsOnly("REQUEST");
        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_UID_HEADER))
            .containsOnly("f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc");
        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_DTSTAMP_HEADER)).isNull();
        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_RECURRENCE_ID_HEADER)).isNull();
        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_SEQUENCE_HEADER)).containsOnly("0");
    }

    @Test
    public void serviceShouldWriteOnlyOneICalendarToHeaders() throws Exception {
        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"));
        Calendar calendar2 = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting_2.ics"));
        ImmutableMap<String, Calendar> icals = ImmutableMap.<String, Calendar>builder()
            .put("key", calendar)
            .put("key2", calendar2)
            .build();

        testee.init(FakeMailetConfig.builder().build());
        Mail mail = FakeMail.builder()
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .attribute(ICALToHeader.ATTRIBUTE_DEFAULT_NAME, icals)
            .build();

        testee.service(mail);

        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_UID_HEADER)).hasSize(1);
    }

    @Test
    public void serviceShouldNotFailOnEmptyMaps() throws Exception {
        ImmutableMap<String, Calendar> icals = ImmutableMap.<String, Calendar>builder()
            .build();

        testee.init(FakeMailetConfig.builder().build());
        Mail mail = FakeMail.builder()
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .attribute(ICALToHeader.ATTRIBUTE_DEFAULT_NAME, icals)
            .build();

        testee.service(mail);

        assertThat(mail.getMessage().getHeader(ICALToHeader.X_MEETING_UID_HEADER)).isNull();
    }
}
