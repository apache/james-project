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

import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.google.common.collect.ImmutableMap;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;

public class ICALToJsonAttributeTest {
    public static final MailAddress SENDER = MailAddressFixture.ANY_AT_JAMES;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private ICALToJsonAttribute testee;

    @Before
    public void setUp() {
        testee = new ICALToJsonAttribute();
    }

    @Test
    public void getMailetInfoShouldReturnExpectedValue() throws Exception {
        assertThat(testee.getMailetInfo()).isEqualTo("ICALToJson Mailet");
    }

    @Test
    public void initShouldSetAttributesWhenAbsent() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        assertThat(testee.getSourceAttributeName()).isEqualTo(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME);
        assertThat(testee.getDestinationAttributeName()).isEqualTo(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME);
    }

    @Test
    public void initShouldThrowOnEmptySourceAttribute() throws Exception {
        expectedException.expect(MessagingException.class);

        testee.init(FakeMailetConfig.builder()
            .setProperty(ICALToJsonAttribute.SOURCE_ATTRIBUTE_NAME, "")
            .build());
    }

    @Test
    public void initShouldThrowOnEmptyRawSourceAttribute() throws Exception {
        expectedException.expect(MessagingException.class);

        testee.init(FakeMailetConfig.builder()
            .setProperty(ICALToJsonAttribute.RAW_SOURCE_ATTRIBUTE_NAME, "")
            .build());
    }

    @Test
    public void initShouldThrowOnEmptyDestinationAttribute() throws Exception {
        expectedException.expect(MessagingException.class);

        testee.init(FakeMailetConfig.builder()
            .setProperty(ICALToJsonAttribute.DESTINATION_ATTRIBUTE_NAME, "")
            .build());
    }

    @Test
    public void initShouldSetAttributesWhenPresent() throws Exception {
        String destination = "myDestination";
        String source = "mySource";
        String raw = "myRaw";
        testee.init(FakeMailetConfig.builder()
            .setProperty(ICALToJsonAttribute.SOURCE_ATTRIBUTE_NAME, source)
            .setProperty(ICALToJsonAttribute.DESTINATION_ATTRIBUTE_NAME, destination)
            .setProperty(ICALToJsonAttribute.RAW_SOURCE_ATTRIBUTE_NAME, raw)
            .build());

        assertThat(testee.getSourceAttributeName()).isEqualTo(source);
        assertThat(testee.getDestinationAttributeName()).isEqualTo(destination);
        assertThat(testee.getRawSourceAttributeName()).isEqualTo(raw);
    }

    @Test
    public void serviceShouldFilterMailsWithoutICALs() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME))
            .isNull();
    }

    @Test
    public void serviceShouldNotFailOnWrongAttributeType() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, "wrong type")
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME))
            .isNull();
    }

    @Test
    public void serviceShouldNotFailOnWrongRawAttributeType() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE_ATTRIBUTE_NAME, "wrong type")
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME))
            .isNull();
    }

    @Test
    public void serviceShouldNotFailOnWrongAttributeParameter() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        ImmutableMap<String, String> wrongParametrizedMap = ImmutableMap.of("key", "value");
        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, wrongParametrizedMap)
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME))
            .isNull();
    }

    @Test
    public void serviceShouldNotFailOnWrongRawAttributeParameter() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        ImmutableMap<String, String> wrongParametrizedMap = ImmutableMap.of("key", "value");
        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE_ATTRIBUTE_NAME, wrongParametrizedMap)
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME))
            .isNull();
    }

    @Test
    public void serviceShouldFilterMailsWithoutSender() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));
        ImmutableMap<String, Calendar> icals = ImmutableMap.of("key", calendar);
        Mail mail = FakeMail.builder()
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE_ATTRIBUTE_NAME, icals)
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME))
            .isNull();
    }

    @Test
    public void serviceShouldAttachEmptyListWhenNoRecipient() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));
        ImmutableMap<String, Calendar> icals = ImmutableMap.of("key", calendar);
        ImmutableMap<String, byte[]> rawIcals = ImmutableMap.of("key", ics);
        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE_ATTRIBUTE_NAME, rawIcals)
            .build();
        testee.service(mail);

        assertThat((Map<?,?>) mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME))
            .isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void serviceShouldAttachJson() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));
        ImmutableMap<String, Calendar> icals = ImmutableMap.of("key", calendar);
        ImmutableMap<String, byte[]> rawIcals = ImmutableMap.of("key", ics);
        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES2;
        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipient(recipient)
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE_ATTRIBUTE_NAME, rawIcals)
            .build();
        testee.service(mail);

        Map<String, byte[]> jsons = (Map<String, byte[]>) mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME);
        assertThat(jsons).hasSize(1);
        assertThatJson(new String(jsons.values().iterator().next(), StandardCharsets.UTF_8))
            .isEqualTo("{" +
                "\"ical\": \"" + toJsonValue(ics) + "\"," +
                "\"sender\": \"" + SENDER.asString() + "\"," +
                "\"recipient\": \"" + recipient.asString() + "\"," +
                "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
                "\"sequence\": \"0\"," +
                "\"dtstamp\": \"20170106T115036Z\"," +
                "\"method\": \"REQUEST\"," +
                "\"recurrence-id\": null" +
                "}");
    }

    private String toJsonValue(byte[] ics) throws UnsupportedEncodingException {
        return new String(JsonStringEncoder.getInstance().quoteAsUTF8(new String(ics, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void serviceShouldAttachJsonForSeveralRecipient() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));
        ImmutableMap<String, Calendar> icals = ImmutableMap.of("key", calendar);
        ImmutableMap<String, byte[]> rawIcals = ImmutableMap.of("key", ics);
        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipients(MailAddressFixture.OTHER_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2)
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE_ATTRIBUTE_NAME, rawIcals)
            .build();
        testee.service(mail);

        Map<String, byte[]> jsons = (Map<String, byte[]>) mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME);
        assertThat(jsons).hasSize(2);
        List<String> actual = toSortedValueList(jsons);

        assertThatJson(actual.get(0)).isEqualTo("{" +
            "\"ical\": \"" + toJsonValue(ics) + "\"," +
            "\"sender\": \"" + SENDER.asString() + "\"," +
            "\"recipient\": \"" + MailAddressFixture.ANY_AT_JAMES2.asString() + "\"," +
            "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
            "\"sequence\": \"0\"," +
            "\"dtstamp\": \"20170106T115036Z\"," +
            "\"method\": \"REQUEST\"," +
            "\"recurrence-id\": null" +
            "}");
        assertThatJson(actual.get(1)).isEqualTo("{" +
            "\"ical\": \"" + toJsonValue(ics) + "\"," +
            "\"sender\": \"" + SENDER.asString() + "\"," +
            "\"recipient\": \"" + MailAddressFixture.OTHER_AT_JAMES.asString() + "\"," +
            "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
            "\"sequence\": \"0\"," +
            "\"dtstamp\": \"20170106T115036Z\"," +
            "\"method\": \"REQUEST\"," +
            "\"recurrence-id\": null" +
            "}");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void serviceShouldAttachJsonForSeveralICALs() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics");
        byte[] ics2 = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting_2.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));
        Calendar calendar2 = new CalendarBuilder().build(new ByteArrayInputStream(ics2));
        ImmutableMap<String, Calendar> icals = ImmutableMap.of("key", calendar, "key2", calendar2);
        ImmutableMap<String, byte[]> rawIcals = ImmutableMap.of("key", ics, "key2", ics2);
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipient(recipient)
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE_ATTRIBUTE_NAME, rawIcals)
            .build();
        testee.service(mail);

        Map<String, byte[]> jsons = (Map<String, byte[]>) mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME);
        assertThat(jsons).hasSize(2);
        List<String> actual = toSortedValueList(jsons);

        assertThatJson(actual.get(0)).isEqualTo("{" +
            "\"ical\": \"" + toJsonValue(ics2) + "\"," +
            "\"sender\": \"" + SENDER.asString() + "\"," +
            "\"recipient\": \"" + recipient.asString() + "\"," +
            "\"uid\": \"f1514f44bf39311568d64072ac247c17656ceafde3b4b3eba961c8c5184cdc6ee047feb2aab16e43439a608f28671ab7c10e754c301b1e32001ad51dd20eac2fc7af20abf4093bbe\"," +
            "\"sequence\": \"0\"," +
            "\"dtstamp\": \"20170103T103250Z\"," +
            "\"method\": \"REQUEST\"," +
            "\"recurrence-id\": null" +
            "}");
        assertThatJson(actual.get(1)).isEqualTo("{" +
            "\"ical\": \"" + toJsonValue(ics) + "\"," +
            "\"sender\": \"" + SENDER.asString() + "\"," +
            "\"recipient\": \"" + recipient.asString() + "\"," +
            "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
            "\"sequence\": \"0\"," +
            "\"dtstamp\": \"20170106T115036Z\"," +
            "\"method\": \"REQUEST\"," +
            "\"recurrence-id\": null" +
            "}");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void serviceShouldFilterInvalidICS() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics");
        byte[] ics2 = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting_without_uid.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));
        Calendar calendar2 = new CalendarBuilder().build(new ByteArrayInputStream(ics2));
        ImmutableMap<String, Calendar> icals = ImmutableMap.of("key", calendar, "key2", calendar2);
        ImmutableMap<String, byte[]> rawIcals = ImmutableMap.of("key", ics, "key2", ics2);
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipient(recipient)
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE_ATTRIBUTE_NAME, rawIcals)
            .build();
        testee.service(mail);

        Map<String, byte[]> jsons = (Map<String, byte[]>) mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME);
        assertThat(jsons).hasSize(1);
        List<String> actual = toSortedValueList(jsons);

        assertThatJson(actual.get(0)).isEqualTo("{" +
            "\"ical\": \"" + toJsonValue(ics) + "\"," +
            "\"sender\": \"" + SENDER.asString() + "\"," +
            "\"recipient\": \"" + recipient.asString() + "\"," +
            "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
            "\"sequence\": \"0\"," +
            "\"dtstamp\": \"20170106T115036Z\"," +
            "\"method\": \"REQUEST\"," +
            "\"recurrence-id\": null" +
            "}");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void serviceShouldFilterNonExistingKeys() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics");
        byte[] ics2 = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting_2.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));
        Calendar calendar2 = new CalendarBuilder().build(new ByteArrayInputStream(ics2));
        ImmutableMap<String, Calendar> icals = ImmutableMap.of("key", calendar, "key2", calendar2);
        ImmutableMap<String, byte[]> rawIcals = ImmutableMap.of("key", ics);
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipient(recipient)
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE_ATTRIBUTE_NAME, rawIcals)
            .build();
        testee.service(mail);

        Map<String, byte[]> jsons = (Map<String, byte[]>) mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME);
        assertThat(jsons).hasSize(1);
        List<String> actual = toSortedValueList(jsons);

        assertThatJson(actual.get(0)).isEqualTo("{" +
            "\"ical\": \"" + toJsonValue(ics) + "\"," +
            "\"sender\": \"" + SENDER.asString() + "\"," +
            "\"recipient\": \"" + recipient.asString() + "\"," +
            "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
            "\"sequence\": \"0\"," +
            "\"dtstamp\": \"20170106T115036Z\"," +
            "\"method\": \"REQUEST\"," +
            "\"recurrence-id\": null" +
            "}");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void serviceShouldUseFromWhenSpecified() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));
        ImmutableMap<String, Calendar> icals = ImmutableMap.of("key", calendar);
        ImmutableMap<String, byte[]> rawIcals = ImmutableMap.of("key", ics);
        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES2;
        String from = MailAddressFixture.OTHER_AT_JAMES.asString();
        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipient(recipient)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addFrom(from))
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE_ATTRIBUTE_NAME, rawIcals)
            .build();
        testee.service(mail);

        Map<String, byte[]> jsons = (Map<String, byte[]>) mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME);
        assertThat(jsons).hasSize(1);
        assertThatJson(new String(jsons.values().iterator().next(), StandardCharsets.UTF_8))
            .isEqualTo("{" +
                "\"ical\": \"" + toJsonValue(ics) + "\"," +
                "\"sender\": \"" + from + "\"," +
                "\"recipient\": \"" + recipient.asString() + "\"," +
                "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
                "\"sequence\": \"0\"," +
                "\"dtstamp\": \"20170106T115036Z\"," +
                "\"method\": \"REQUEST\"," +
                "\"recurrence-id\": null" +
                "}");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void serviceShouldSupportMimeMessagesWithoutFromFields() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));
        ImmutableMap<String, Calendar> icals = ImmutableMap.of("key", calendar);
        ImmutableMap<String, byte[]> rawIcals = ImmutableMap.of("key", ics);
        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES2;
        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipient(recipient)
            .mimeMessage(MimeMessageBuilder.defaultMimeMessage())
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE_ATTRIBUTE_NAME, rawIcals)
            .build();
        testee.service(mail);

        Map<String, byte[]> jsons = (Map<String, byte[]>) mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME);
        assertThat(jsons).hasSize(1);
        assertThatJson(new String(jsons.values().iterator().next(), StandardCharsets.UTF_8))
            .isEqualTo("{" +
                "\"ical\": \"" + toJsonValue(ics) + "\"," +
                "\"sender\": \"" + SENDER.asString() + "\"," +
                "\"recipient\": \"" + recipient.asString() + "\"," +
                "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
                "\"sequence\": \"0\"," +
                "\"dtstamp\": \"20170106T115036Z\"," +
                "\"method\": \"REQUEST\"," +
                "\"recurrence-id\": null" +
                "}");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void serviceShouldUseFromWhenSpecifiedAndNoSender() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));
        ImmutableMap<String, Calendar> icals = ImmutableMap.of("key", calendar);
        ImmutableMap<String, byte[]> rawIcals = ImmutableMap.of("key", ics);
        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES2;
        String from = MailAddressFixture.OTHER_AT_JAMES.asString();
        Mail mail = FakeMail.builder()
            .recipient(recipient)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addFrom(from))
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE_ATTRIBUTE_NAME, rawIcals)
            .build();
        testee.service(mail);

        Map<String, byte[]> jsons = (Map<String, byte[]>) mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME);
        assertThat(jsons).hasSize(1);
        assertThatJson(new String(jsons.values().iterator().next(), StandardCharsets.UTF_8))
            .isEqualTo("{" +
                "\"ical\": \"" + toJsonValue(ics) + "\"," +
                "\"sender\": \"" + from + "\"," +
                "\"recipient\": \"" + recipient.asString() + "\"," +
                "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
                "\"sequence\": \"0\"," +
                "\"dtstamp\": \"20170106T115036Z\"," +
                "\"method\": \"REQUEST\"," +
                "\"recurrence-id\": null" +
                "}");
    }

    private List<String> toSortedValueList(Map<String, byte[]> jsons) {
        return jsons.values()
                .stream()
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .sorted()
                .collect(Collectors.toList());
    }
}
