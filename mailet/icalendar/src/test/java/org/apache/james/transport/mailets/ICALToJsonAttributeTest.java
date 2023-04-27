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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.google.common.collect.ImmutableMap;

import net.fortuna.ical4j.data.CalendarBuilder;

public class ICALToJsonAttributeTest {
    private static final MailAddress SENDER = MailAddressFixture.ANY_AT_JAMES;
    @SuppressWarnings("unchecked")
    private static final Class<Map<String, AttributeValue<byte[]>>> MAP_STRING_BYTES_CLASS = (Class<Map<String, AttributeValue<byte[]>>>) (Object) Map.class;

    private ICALToJsonAttribute testee;

    @BeforeEach
    void setUp() {
        testee = new ICALToJsonAttribute();
    }

    @Test
    void getMailetInfoShouldReturnExpectedValue() {
        assertThat(testee.getMailetInfo()).isEqualTo("ICALToJson Mailet");
    }

    @Test
    void initShouldSetAttributesWhenAbsent() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        assertThat(testee.getSourceAttributeName()).isEqualTo(ICALToJsonAttribute.DEFAULT_SOURCE);
        assertThat(testee.getDestinationAttributeName()).isEqualTo(ICALToJsonAttribute.DEFAULT_DESTINATION);
    }

    @Test
    void initShouldThrowOnEmptySourceAttribute() {
        assertThatThrownBy(() -> testee.init(FakeMailetConfig.builder()
                .setProperty(ICALToJsonAttribute.SOURCE_ATTRIBUTE_NAME, "")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldThrowOnEmptyRawSourceAttribute() {
        assertThatThrownBy(() -> testee.init(FakeMailetConfig.builder()
                .setProperty(ICALToJsonAttribute.RAW_SOURCE_ATTRIBUTE_NAME, "")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldThrowOnEmptyDestinationAttribute() {
        assertThatThrownBy(() -> testee.init(FakeMailetConfig.builder()
                .setProperty(ICALToJsonAttribute.DESTINATION_ATTRIBUTE_NAME, "")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldSetAttributesWhenPresent() throws Exception {
        String destination = "myDestination";
        String source = "mySource";
        String raw = "myRaw";
        testee.init(FakeMailetConfig.builder()
            .setProperty(ICALToJsonAttribute.SOURCE_ATTRIBUTE_NAME, source)
            .setProperty(ICALToJsonAttribute.DESTINATION_ATTRIBUTE_NAME, destination)
            .setProperty(ICALToJsonAttribute.RAW_SOURCE_ATTRIBUTE_NAME, raw)
            .build());

        assertThat(testee.getSourceAttributeName().asString()).isEqualTo(source);
        assertThat(testee.getDestinationAttributeName().asString()).isEqualTo(destination);
        assertThat(testee.getRawSourceAttributeName().asString()).isEqualTo(raw);
    }

    @Test
    void serviceShouldFilterMailsWithoutICALs() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION))
            .isEmpty();
    }

    @Test
    void serviceShouldNotFailOnWrongAttributeType() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_SOURCE, AttributeValue.of("wrong type")))
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION))
            .isEmpty();
    }

    @Test
    void serviceShouldNotFailOnWrongRawAttributeType() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE, AttributeValue.of("wrong type")))
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION))
            .isEmpty();
    }

    @Test
    void serviceShouldNotFailOnWrongAttributeParameter() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        ImmutableMap<String, AttributeValue<String>> wrongParametrizedMap = ImmutableMap.of("key", AttributeValue.of("value"));
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_SOURCE, AttributeValue.ofAny(wrongParametrizedMap)))
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION))
            .isEmpty();
    }

    @Test
    void serviceShouldNotFailOnWrongRawAttributeParameter() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        ImmutableMap<String, AttributeValue<String>> wrongParametrizedMap = ImmutableMap.of("key", AttributeValue.of("value"));
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE, AttributeValue.ofAny(wrongParametrizedMap)))
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION))
            .isEmpty();
    }

    @Test
    void serviceShouldFilterMailsWithoutSender() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        AttributeValue<byte[]> ics = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics"));
        AttributeValue<Object> calendar = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics.getValue())));
        Map<String, AttributeValue<?>> icals = ImmutableMap.of("key", calendar);
        Map<String, AttributeValue<?>> rawIcals = ImmutableMap.of("key", ics);
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_SOURCE, AttributeValue.ofAny(icals)))
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE, AttributeValue.ofAny(icals)))
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION))
            .isEmpty();
    }

    @Test
    void serviceShouldFilterICSWithoutEvents() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        AttributeValue<byte[]> ics = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/no_event.ics"));
        AttributeValue<Object> calendar = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics.getValue())));
        Map<String, AttributeValue<?>> icals = ImmutableMap.of("key", calendar);
        Map<String, AttributeValue<?>> rawIcals = ImmutableMap.of("key", ics);
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_SOURCE, AttributeValue.ofAny(icals)))
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE, AttributeValue.ofAny(rawIcals)))
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION))
            .isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void serviceShouldNotAttachWhenNoRecipient() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        AttributeValue<byte[]> ics = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics"));
        AttributeValue<Object> calendar = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics.getValue())));
        Map<String, AttributeValue<?>> icals = ImmutableMap.of("key", calendar);
        Map<String, AttributeValue<?>> rawIcals = ImmutableMap.of("key", ics);
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_SOURCE, AttributeValue.ofAny(icals)))
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE, AttributeValue.ofAny(rawIcals)))
            .build();
        testee.service(mail);

        assertThat(AttributeUtils.getValueAndCastFromMail(mail, ICALToJsonAttribute.DEFAULT_DESTINATION, Map.class))
            .isEmpty();
    }

    @Test
    void serviceShouldAttachJson() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        AttributeValue<byte[]> ics = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics"));
        AttributeValue<Object> calendar = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics.getValue())));
        Map<String, AttributeValue<?>> icals = ImmutableMap.of("key", calendar);
        Map<String, AttributeValue<?>> rawIcals = ImmutableMap.of("key", ics);
        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES2;
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .recipient(recipient)
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_SOURCE, AttributeValue.ofAny(icals)))
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE, AttributeValue.ofAny(rawIcals)))
            .build();
        testee.service(mail);

        assertThat(AttributeUtils.getValueAndCastFromMail(mail, ICALToJsonAttribute.DEFAULT_DESTINATION, MAP_STRING_BYTES_CLASS))
            .isPresent()
            .hasValueSatisfying(jsons -> {
                assertThat(jsons).hasSize(1);
                assertThatJson(new String(jsons.values().iterator().next().getValue(), StandardCharsets.UTF_8))
                    .isEqualTo("{" +
                        "\"ical\": \"" + toJsonValue(ics.getValue()) + "\"," +
                        "\"sender\": \"" + SENDER.asString() + "\"," +
                        "\"replyTo\": \"" + SENDER.asString() + "\"," +
                        "\"recipient\": \"" + recipient.asString() + "\"," +
                        "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
                        "\"sequence\": \"0\"," +
                        "\"dtstamp\": \"20170106T115036Z\"," +
                        "\"method\": \"REQUEST\"," +
                        "\"recurrence-id\": null" +
                        "}");
            });
    }

    private String toJsonValue(byte[] ics) {
        return new String(new JsonStringEncoder().quoteAsUTF8(new String(ics, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @MethodSource("validReplyToHeaders")
    void serviceShouldAttachJsonWithTheReplyToAttributeValueWhenPresent(String replyToHeader) throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        AttributeValue<byte[]> ics = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics"));
        AttributeValue<Object> calendar = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics.getValue())));
        Map<String, AttributeValue<?>> icals = ImmutableMap.of("key", calendar);
        Map<String, AttributeValue<?>> rawIcals = ImmutableMap.of("key", ics);
        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES2;
        MailAddress replyTo = MailAddressFixture.OTHER_AT_JAMES;
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .recipient(recipient)
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_SOURCE, AttributeValue.ofAny(icals)))
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE, AttributeValue.ofAny(rawIcals)))
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(ICALToJsonAttribute.REPLY_TO_HEADER_NAME, replyToHeader))
            .build();
        testee.service(mail);

        assertThat(AttributeUtils.getValueAndCastFromMail(mail, ICALToJsonAttribute.DEFAULT_DESTINATION, MAP_STRING_BYTES_CLASS))
            .isPresent()
            .hasValueSatisfying(jsons -> {
                assertThat(jsons).hasSize(1);
                assertThatJson(new String(jsons.values().iterator().next().getValue(), StandardCharsets.UTF_8))
                    .isEqualTo("{" +
                        "\"ical\": \"" + toJsonValue(ics.getValue()) + "\"," +
                        "\"sender\": \"" + SENDER.asString() + "\"," +
                        "\"replyTo\": \"" + replyTo.asString() + "\"," +
                        "\"recipient\": \"" + recipient.asString() + "\"," +
                        "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
                        "\"sequence\": \"0\"," +
                        "\"dtstamp\": \"20170106T115036Z\"," +
                        "\"method\": \"REQUEST\"," +
                        "\"recurrence-id\": null" +
                        "}");
            });
    }

    private static Stream<Arguments> validReplyToHeaders() {
        String address = MailAddressFixture.OTHER_AT_JAMES.asString();
        return Stream.of(
                address,
                "<" + address + ">",
                "\"Bob\" <" + address + ">",
                "\"Bob\"\n      <" + address + ">",
                " =?UTF-8?Q?Beno=c3=aet_TELLIER?= <" + address + ">")
            .map(Arguments::of);
    }

    @Test
    void serviceShouldAttachJsonWithTheSenderAsReplyToAttributeValueWhenReplyToIsInvalid() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        AttributeValue<byte[]> ics = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics"));
        AttributeValue<Object> calendar = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics.getValue())));
        Map<String, AttributeValue<?>> icals = ImmutableMap.of("key", calendar);
        Map<String, AttributeValue<?>> rawIcals = ImmutableMap.of("key", ics);
        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES2;
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .recipient(recipient)
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_SOURCE, AttributeValue.ofAny(icals)))
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE, AttributeValue.ofAny(rawIcals)))
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(ICALToJsonAttribute.REPLY_TO_HEADER_NAME, "inv@lid.m@il.adr"))
            .build();
        testee.service(mail);

        assertThat(AttributeUtils.getValueAndCastFromMail(mail, ICALToJsonAttribute.DEFAULT_DESTINATION, MAP_STRING_BYTES_CLASS))
            .isPresent()
            .hasValueSatisfying(jsons -> {
                assertThat(jsons).hasSize(1);
                assertThatJson(new String(jsons.values().iterator().next().getValue(), StandardCharsets.UTF_8))
                    .isEqualTo("{" +
                        "\"ical\": \"" + toJsonValue(ics.getValue()) + "\"," +
                        "\"sender\": \"" + SENDER.asString() + "\"," +
                        "\"replyTo\": \"" + SENDER.asString() + "\"," +
                        "\"recipient\": \"" + recipient.asString() + "\"," +
                        "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
                        "\"sequence\": \"0\"," +
                        "\"dtstamp\": \"20170106T115036Z\"," +
                        "\"method\": \"REQUEST\"," +
                        "\"recurrence-id\": null" +
                        "}");
            });
    }

    @Test
    void serviceShouldAttachJsonForSeveralRecipient() throws Exception {
        testee.init(FakeMailetConfig.builder().build());
        AttributeValue<byte[]> ics = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics"));
        AttributeValue<Object> calendar = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics.getValue())));
        Map<String, AttributeValue<?>> icals = ImmutableMap.of("key", calendar);
        Map<String, AttributeValue<?>> rawIcals = ImmutableMap.of("key", ics);
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .recipients(MailAddressFixture.OTHER_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2)
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_SOURCE, AttributeValue.ofAny(icals)))
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE, AttributeValue.ofAny(rawIcals)))
            .build();
        testee.service(mail);

        assertThat(AttributeUtils.getValueAndCastFromMail(mail, ICALToJsonAttribute.DEFAULT_DESTINATION, MAP_STRING_BYTES_CLASS))
                .isPresent()
                .hasValueSatisfying(jsons -> {
                    assertThat(jsons).hasSize(2);
                    List<String> actual = toSortedValueList(jsons);

                    assertThatJson(actual.get(0)).isEqualTo("{" +
                        "\"ical\": \"" + toJsonValue(ics.getValue()) + "\"," +
                        "\"sender\": \"" + SENDER.asString() + "\"," +
                        "\"recipient\": \"" + MailAddressFixture.ANY_AT_JAMES2.asString() + "\"," +
                        "\"replyTo\": \"" + SENDER.asString() + "\"," +
                        "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
                        "\"sequence\": \"0\"," +
                        "\"dtstamp\": \"20170106T115036Z\"," +
                        "\"method\": \"REQUEST\"," +
                        "\"recurrence-id\": null" +
                        "}");
                });
    }

    @Test
    void serviceShouldAttachJsonForSeveralICALs() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        AttributeValue<byte[]> ics = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics"));
        AttributeValue<byte[]> ics2 = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting_2.ics"));
        AttributeValue<Object> calendar = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics.getValue())));
        AttributeValue<Object> calendar2 = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics2.getValue())));
        Map<String, AttributeValue<?>> icals = ImmutableMap.of("key", calendar, "key2", calendar2);
        Map<String, AttributeValue<?>> rawIcals = ImmutableMap.of("key", ics, "key2", ics2);
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .recipient(recipient)
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_SOURCE, AttributeValue.ofAny(icals)))
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE, AttributeValue.ofAny(rawIcals)))
            .build();
        testee.service(mail);

        assertThat(AttributeUtils.getValueAndCastFromMail(mail, ICALToJsonAttribute.DEFAULT_DESTINATION, MAP_STRING_BYTES_CLASS))
                .isPresent()
                .hasValueSatisfying(jsons -> {
                    assertThat(jsons).hasSize(2);
                    List<String> actual = toSortedValueList(jsons);

                    assertThatJson(actual.get(0)).isEqualTo("{" +
                        "\"ical\": \"" + toJsonValue(ics2.getValue()) + "\"," +
                        "\"sender\": \"" + SENDER.asString() + "\"," +
                        "\"recipient\": \"" + recipient.asString() + "\"," +
                        "\"replyTo\": \"" + SENDER.asString() + "\"," +
                        "\"uid\": \"f1514f44bf39311568d64072ac247c17656ceafde3b4b3eba961c8c5184cdc6ee047feb2aab16e43439a608f28671ab7c10e754c301b1e32001ad51dd20eac2fc7af20abf4093bbe\"," +
                        "\"sequence\": \"0\"," +
                        "\"dtstamp\": \"20170103T103250Z\"," +
                        "\"method\": \"REQUEST\"," +
                        "\"recurrence-id\": null" +
                        "}");
                    assertThatJson(actual.get(1)).isEqualTo("{" +
                        "\"ical\": \"" + toJsonValue(ics.getValue()) + "\"," +
                        "\"sender\": \"" + SENDER.asString() + "\"," +
                        "\"recipient\": \"" + recipient.asString() + "\"," +
                        "\"replyTo\": \"" + SENDER.asString() + "\"," +
                        "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
                        "\"sequence\": \"0\"," +
                        "\"dtstamp\": \"20170106T115036Z\"," +
                        "\"method\": \"REQUEST\"," +
                        "\"recurrence-id\": null" +
                        "}");
                    });
    }

    @Test
    void serviceShouldFilterInvalidICS() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        AttributeValue<byte[]> ics = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics"));
        AttributeValue<byte[]> ics2 = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting_without_uid.ics"));
        AttributeValue<Object> calendar = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics.getValue())));
        AttributeValue<Object> calendar2 = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics2.getValue())));
        Map<String, AttributeValue<?>> icals = ImmutableMap.of("key", calendar, "key2", calendar2);
        Map<String, AttributeValue<?>> rawIcals = ImmutableMap.of("key", ics, "key2", ics2);
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .recipient(recipient)
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_SOURCE, AttributeValue.of(icals)))
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE, AttributeValue.ofAny(rawIcals)))
            .build();
        testee.service(mail);

        assertThat(AttributeUtils.getValueAndCastFromMail(mail, ICALToJsonAttribute.DEFAULT_DESTINATION, MAP_STRING_BYTES_CLASS))
                .isPresent()
                .hasValueSatisfying(jsons -> {
                    assertThat(jsons).hasSize(1);
                    List<String> actual = toSortedValueList(jsons);

                    assertThatJson(actual.get(0)).isEqualTo("{" +
                        "\"ical\": \"" + toJsonValue(ics.getValue()) + "\"," +
                        "\"sender\": \"" + SENDER.asString() + "\"," +
                        "\"recipient\": \"" + recipient.asString() + "\"," +
                        "\"replyTo\": \"" + SENDER.asString() + "\"," +
                        "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
                        "\"sequence\": \"0\"," +
                        "\"dtstamp\": \"20170106T115036Z\"," +
                        "\"method\": \"REQUEST\"," +
                        "\"recurrence-id\": null" +
                        "}");
                });
    }

    @Test
    void serviceShouldFilterNonExistingKeys() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        AttributeValue<byte[]> ics = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics"));
        AttributeValue<byte[]> ics2 = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting_2.ics"));
        AttributeValue<Object> calendar = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics.getValue())));
        AttributeValue<Object> calendar2 = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics2.getValue())));
        Map<String, AttributeValue<?>> icals = ImmutableMap.of("key", calendar, "key2", calendar2);
        Map<String, AttributeValue<?>> rawIcals = ImmutableMap.of("key", ics);
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .recipient(recipient)
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_SOURCE, AttributeValue.ofAny(icals)))
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE, AttributeValue.ofAny(rawIcals)))
            .build();
        testee.service(mail);

        assertThat(AttributeUtils.getValueAndCastFromMail(mail, ICALToJsonAttribute.DEFAULT_DESTINATION, MAP_STRING_BYTES_CLASS))
                .isPresent()
                .hasValueSatisfying(jsons -> {
                    assertThat(jsons).hasSize(1);
                    List<String> actual = toSortedValueList(jsons);

                    assertThatJson(actual.get(0)).isEqualTo("{" +
                        "\"ical\": \"" + toJsonValue(ics.getValue()) + "\"," +
                        "\"sender\": \"" + SENDER.asString() + "\"," +
                        "\"replyTo\": \"" + SENDER.asString() + "\"," +
                        "\"recipient\": \"" + recipient.asString() + "\"," +
                        "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
                        "\"sequence\": \"0\"," +
                        "\"dtstamp\": \"20170106T115036Z\"," +
                        "\"method\": \"REQUEST\"," +
                        "\"recurrence-id\": null" +
                        "}");
                });
    }

    @Test
    void serviceShouldUseFromWhenSpecified() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        AttributeValue<byte[]> ics = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics"));
        AttributeValue<Object> calendar = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics.getValue())));
        Map<String, AttributeValue<?>> icals = ImmutableMap.of("key", calendar);
        Map<String, AttributeValue<?>> rawIcals = ImmutableMap.of("key", ics);
        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES2;
        String from = MailAddressFixture.OTHER_AT_JAMES.asString();
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .recipient(recipient)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addFrom(from))
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_SOURCE, AttributeValue.ofAny(icals)))
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE, AttributeValue.ofAny(rawIcals)))
            .build();
        testee.service(mail);

        assertThat(AttributeUtils.getValueAndCastFromMail(mail, ICALToJsonAttribute.DEFAULT_DESTINATION, MAP_STRING_BYTES_CLASS))
                .isPresent()
                .hasValueSatisfying(jsons -> {
                    assertThat(jsons).hasSize(1);
                    assertThatJson(new String(jsons.values().iterator().next().getValue(), StandardCharsets.UTF_8))
                        .isEqualTo("{" +
                            "\"ical\": \"" + toJsonValue(ics.getValue()) + "\"," +
                            "\"sender\": \"" + from + "\"," +
                            "\"recipient\": \"" + recipient.asString() + "\"," +
                            "\"replyTo\": \"" + from + "\"," +
                            "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
                            "\"sequence\": \"0\"," +
                            "\"dtstamp\": \"20170106T115036Z\"," +
                            "\"method\": \"REQUEST\"," +
                            "\"recurrence-id\": null" +
                            "}");
                });
    }

    @Test
    void serviceShouldSupportMimeMessagesWithoutFromFields() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        AttributeValue<byte[]> ics = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics"));
        AttributeValue<Object> calendar = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics.getValue())));
        Map<String, AttributeValue<?>> icals = ImmutableMap.of("key", calendar);
        Map<String, AttributeValue<?>> rawIcals = ImmutableMap.of("key", ics);
        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES2;
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender(SENDER)
            .recipient(recipient)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_SOURCE, AttributeValue.ofAny(icals)))
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE, AttributeValue.ofAny(rawIcals)))
            .build();
        testee.service(mail);

        assertThat(AttributeUtils.getValueAndCastFromMail(mail, ICALToJsonAttribute.DEFAULT_DESTINATION, MAP_STRING_BYTES_CLASS))
                .isPresent()
                .hasValueSatisfying(jsons -> {
                    assertThat(jsons).hasSize(1);
                    assertThatJson(new String(jsons.values().iterator().next().getValue(), StandardCharsets.UTF_8))
                        .isEqualTo("{" +
                            "\"ical\": \"" + toJsonValue(ics.getValue()) + "\"," +
                            "\"sender\": \"" + SENDER.asString() + "\"," +
                            "\"recipient\": \"" + recipient.asString() + "\"," +
                            "\"replyTo\": \"" + SENDER.asString() + "\"," +
                            "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
                            "\"sequence\": \"0\"," +
                            "\"dtstamp\": \"20170106T115036Z\"," +
                            "\"method\": \"REQUEST\"," +
                            "\"recurrence-id\": null" +
                            "}");
                });
    }

    @Test
    void serviceShouldUseFromWhenSpecifiedAndNoSender() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        AttributeValue<byte[]> ics = AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics"));
        AttributeValue<Object> calendar = AttributeValue.ofUnserializable(new CalendarBuilder().build(new ByteArrayInputStream(ics.getValue())));
        Map<String, AttributeValue<?>> icals = ImmutableMap.of("key", calendar);
        Map<String, AttributeValue<?>> rawIcals = ImmutableMap.of("key", ics);
        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES2;
        String from = MailAddressFixture.OTHER_AT_JAMES.asString();
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipient(recipient)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addFrom(from))
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_SOURCE, AttributeValue.ofAny(icals)))
            .attribute(new Attribute(ICALToJsonAttribute.DEFAULT_RAW_SOURCE, AttributeValue.ofAny(rawIcals)))
            .build();
        testee.service(mail);

        assertThat(AttributeUtils.getValueAndCastFromMail(mail, ICALToJsonAttribute.DEFAULT_DESTINATION, MAP_STRING_BYTES_CLASS))
                .isPresent()
                .hasValueSatisfying(jsons -> {
                    assertThat(jsons).hasSize(1);
                    assertThatJson(new String(jsons.values().iterator().next().getValue(), StandardCharsets.UTF_8))
                        .isEqualTo("{" +
                            "\"ical\": \"" + toJsonValue(ics.getValue()) + "\"," +
                            "\"sender\": \"" + from + "\"," +
                            "\"recipient\": \"" + recipient.asString() + "\"," +
                            "\"replyTo\": \"" + from + "\"," +
                            "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
                            "\"sequence\": \"0\"," +
                            "\"dtstamp\": \"20170106T115036Z\"," +
                            "\"method\": \"REQUEST\"," +
                            "\"recurrence-id\": null" +
                            "}");
                });
    }

    private List<String> toSortedValueList(Map<String, AttributeValue<byte[]>> jsons) {
        return jsons.values()
                .stream()
                .map(bytes -> new String(bytes.getValue(), StandardCharsets.UTF_8))
                .sorted()
                .collect(Collectors.toList());
    }
}
