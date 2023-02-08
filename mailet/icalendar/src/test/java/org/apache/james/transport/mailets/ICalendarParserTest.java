/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
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

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

import javax.mail.MessagingException;

import org.apache.james.util.ClassLoaderUtils;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import net.fortuna.ical4j.model.Calendar;

class ICalendarParserTest {
    static final String DESTINATION_ATTRIBUTE = "destinationAttribute";
    static final String SOURCE_ATTRIBUTE = "sourceAttribute";

    static final String DESTINATION_CUSTOM_ATTRIBUTE = "ics.dest.attribute";
    static final AttributeName DESTINATION_CUSTOM_ATTRIBUTE_NAME = AttributeName.of(DESTINATION_CUSTOM_ATTRIBUTE);
    static final String SOURCE_CUSTOM_ATTRIBUTE = "ics.source.attribute";
    static final AttributeName SOURCE_CUSTOM_ATTRIBUTE_NAME = AttributeName.of(SOURCE_CUSTOM_ATTRIBUTE);

    static final String RIGHT_ICAL_VALUE = "BEGIN:VCALENDAR\n" +
        "END:VCALENDAR";
    static final String FAULTY_ICAL_VALUE = "BEGIN:VCALENDAR\n" +
        "BEGIN:VEVENT\n" +
        "BEGIN:VALARM\n" +
        "TRIGGER:P1800S\n" + // net.fortuna.ical4j.data.ParserException: Error at line 9:Text cannot be parsed to a Duration
        "ACTION:display\n" +
        "DESCRIPTION:XXXXXXXXXXXXX\n" +
        "END:VALARM\n" +
        "CREATED:20200903T145836Z\n" +
        "LAST-MODIFIED:20200910T095359Z\n" +
        "DTSTAMP:20200910T095359Z\n" +
        "DTSTART:20200915T120000Z\n" +
        "DURATION:PT2H30M\n" +
        "TRANSP:OPAQUE\n" +
        "SEQUENCE:2\n" +
        "SUMMARY:XXXXXXXX\n" +
        "DESCRIPTION:\n" +
        "CLASS:PUBLIC\n" +
        "PRIORITY:5\n" +
        "ORGANIZER;X-OBM-ID=612;CN=XXXX:MAILTO:xxxxxx@linagora.com\n" +
        "X-OBM-DOMAIN:linagora.com\n" +
        "X-OBM-DOMAIN-UUID:02874f7c-d10e-102f-acda-0015176f7922\n" +
        "LOCATION:SULLY-217R\n" +
        "CATEGORIES:\n" +
        "X-OBM-COLOR:\n" +
        "X-OBM-ALERT;X-OBM-ID=612:1800\n" +
        "UID:f1514f44bf39311568d640729686167c77935c03dc972e2286e115e86cf5d7a6e047fe\n" +
        " b2aab16e43439a608f28671ab7c10e754c29879684a2ba5b1e4ed3eea7211d629898482b3e\n" +
        "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=XXXXXXX;PARTSTAT=ACCEPTED;X\n" +
        " -OBM-ID=612:MAILTO:xxxxx@linagora.com\n" +
        "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=YYYYYYYY;PARTSTAT=DECLINED;X-OB\n" +
        " M-ID=1329:MAILTO:yyyyyy@linagora.com\n" +
        "END:VEVENT\n" +
        "END:VCALENDAR";

    static final String WRONG_ICAL_VALUE = "anyValue";
    @SuppressWarnings("unchecked")
    static final Class<Map<String, AttributeValue<Serializable>>> MAP_STRING_CALENDAR_CLASS = (Class<Map<String, AttributeValue<Serializable>>>) (Object) Map.class;

    ICalendarParser mailet = new ICalendarParser();

    @Test
    void initShouldSetSourceAttributeWhenAbsent() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .build());

        assertThat(mailet.getSourceAttributeName().asString()).isEqualTo(ICalendarParser.SOURCE_ATTRIBUTE_PARAMETER_DEFAULT_VALUE);
    }

    @Test
    void initShouldSetDestinationAttributeWhenAbsent() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .build());

        assertThat(mailet.getDestinationAttributeName().asString()).isEqualTo(ICalendarParser.DESTINATION_ATTRIBUTE_PARAMETER_DEFAULT_VALUE);
    }

    @Test
    void initShouldSetSourceAttributeWhenPresent() throws Exception {
        String sourceAttribute = "sourceAttribute";
        mailet.init(FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .setProperty(ICalendarParser.SOURCE_ATTRIBUTE_PARAMETER_NAME, sourceAttribute)
            .build());

        assertThat(mailet.getSourceAttributeName().asString()).isEqualTo(sourceAttribute);
    }

    @Test
    void initShouldSetDestinationAttributeWhenPresent() throws Exception {
        String destinationAttribute = "destinationAttribute";
        mailet.init(FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .setProperty(ICalendarParser.DESTINATION_ATTRIBUTE_PARAMETER_NAME, destinationAttribute)
            .build());

        assertThat(mailet.getDestinationAttributeName().asString()).isEqualTo(destinationAttribute);
    }

    @Test
    void initShouldThrowOnEmptySourceAttribute() {
        assertThatThrownBy(() -> mailet.init(FakeMailetConfig.builder()
                .mailetName("ICalendarParser")
                .setProperty(ICalendarParser.SOURCE_ATTRIBUTE_PARAMETER_NAME, "")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldThrowOnEmptyDestinationAttribute() {
        assertThatThrownBy(() -> mailet.init(FakeMailetConfig.builder()
                .mailetName("ICalendarParser")
                .setProperty(ICalendarParser.DESTINATION_ATTRIBUTE_PARAMETER_NAME, "")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void serviceShouldNotSetCalendarDataIntoMailAttributeWhenNoSourceAttribute() throws Exception {
        FakeMailetConfig mailetConfiguration = FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .setProperty(DESTINATION_ATTRIBUTE, DESTINATION_CUSTOM_ATTRIBUTE)
            .build();

        mailet.init(mailetConfiguration);

        Mail mail = FakeMail.builder()
            .name("mail")
            .build();

        mailet.service(mail);

        assertThat(mail.attributes()).isEmpty();
    }

    @Test
    void serviceShouldSetEmptyCalendarDataIntoMailAttributeWhenEmptyICSAttachments() throws Exception {
        FakeMailetConfig mailetConfiguration = FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .setProperty(SOURCE_ATTRIBUTE, SOURCE_CUSTOM_ATTRIBUTE)
            .setProperty(DESTINATION_ATTRIBUTE, DESTINATION_CUSTOM_ATTRIBUTE)
            .build();

        mailet.init(mailetConfiguration);

        Mail mail = FakeMail.builder()
            .name("mail")
            .attribute(new Attribute(SOURCE_CUSTOM_ATTRIBUTE_NAME, AttributeValue.of(ImmutableMap.of())))
            .build();

        mailet.service(mail);

        assertThat(mail.getAttribute(DESTINATION_CUSTOM_ATTRIBUTE_NAME))
            .isPresent()
            .hasValueSatisfying(attribute -> assertThat((Map<?, ?>) attribute.getValue().value()).isEmpty());
    }

    @Test
    void serviceShouldNotSetCalendarDataIntoMailAttributeWhenSourceAttributeIsNotAMap() throws Exception {
        FakeMailetConfig mailetConfiguration = FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .setProperty(SOURCE_ATTRIBUTE, SOURCE_CUSTOM_ATTRIBUTE)
            .setProperty(DESTINATION_ATTRIBUTE, DESTINATION_CUSTOM_ATTRIBUTE)
            .build();

        mailet.init(mailetConfiguration);

        Mail mail = FakeMail.builder()
            .name("mail")
            .attribute(new Attribute(SOURCE_CUSTOM_ATTRIBUTE_NAME, AttributeValue.of("anyValue")))
            .build();

        mailet.service(mail);

        assertThat(mail.getAttribute(DESTINATION_CUSTOM_ATTRIBUTE_NAME)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void serviceShouldReturnRightMapOfCalendarWhenRightAttachments() throws Exception {
        FakeMailetConfig mailetConfiguration = FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .setProperty(SOURCE_ATTRIBUTE, SOURCE_CUSTOM_ATTRIBUTE)
            .setProperty(DESTINATION_ATTRIBUTE, DESTINATION_CUSTOM_ATTRIBUTE)
            .build();

        mailet.init(mailetConfiguration);

        Map<String, AttributeValue<byte[]>> attachments = ImmutableMap.<String, AttributeValue<byte[]>>builder()
            .put("key", AttributeValue.of(RIGHT_ICAL_VALUE.getBytes()))
            .build();

        Mail mail = FakeMail.builder()
            .name("mail")
            .attribute(makeCustomSourceAttribute((Serializable) attachments))
            .build();

        mailet.service(mail);

        Optional<Map<String, Calendar>> expectedCalendars = AttributeUtils.getValueAndCastFromMail(mail, DESTINATION_CUSTOM_ATTRIBUTE_NAME, (Class<Map<String, Calendar>>)(Object) Map.class);
        assertThat(expectedCalendars)
            .isPresent()
            .hasValueSatisfying(calendars ->
                    assertThat(calendars)
                        .hasSize(1));
    }

    @Test
    void serviceShouldFilterResultWhenErrorParsing() throws Exception {
        FakeMailetConfig mailetConfiguration = FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .setProperty(SOURCE_ATTRIBUTE, SOURCE_CUSTOM_ATTRIBUTE)
            .setProperty(DESTINATION_ATTRIBUTE, DESTINATION_CUSTOM_ATTRIBUTE)
            .build();

        mailet.init(mailetConfiguration);

        Map<String, AttributeValue<byte[]>> attachments = ImmutableMap.<String, AttributeValue<byte[]>>builder()
            .put("key1", AttributeValue.of(WRONG_ICAL_VALUE.getBytes()))
            .put("key2", AttributeValue.of(RIGHT_ICAL_VALUE.getBytes()))
            .build();
        Mail mail = FakeMail.builder()
            .name("mail")
            .attribute(makeCustomSourceAttribute((Serializable) attachments))
            .build();

        mailet.service(mail);

        Optional<Map<String, AttributeValue<Serializable>>> expectedCalendars = AttributeUtils.getValueAndCastFromMail(mail, DESTINATION_CUSTOM_ATTRIBUTE_NAME, MAP_STRING_CALENDAR_CLASS);
        Map.Entry<String, AttributeValue<Serializable>> expectedCalendar = Maps.immutableEntry("key2", AttributeValue.ofSerializable(new Calendar()));

        assertThat(expectedCalendars).hasValueSatisfying(calendars ->
            assertThat(calendars)
                .hasSize(1)
                .containsExactly(expectedCalendar));
    }

    @Test
    void parsingShouldRecoverFromInvalidProperties() throws Exception {
        FakeMailetConfig mailetConfiguration = FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .setProperty(SOURCE_ATTRIBUTE, SOURCE_CUSTOM_ATTRIBUTE)
            .setProperty(DESTINATION_ATTRIBUTE, DESTINATION_CUSTOM_ATTRIBUTE)
            .build();

        mailet.init(mailetConfiguration);

        Map<String, AttributeValue<byte[]>> attachments = ImmutableMap.<String, AttributeValue<byte[]>>builder()
            .put("key1", AttributeValue.of(FAULTY_ICAL_VALUE.getBytes()))
            .build();
        Mail mail = FakeMail.builder()
            .name("mail")
            .attribute(makeCustomSourceAttribute((Serializable) attachments))
            .build();

        mailet.service(mail);

        Optional<Map<String, AttributeValue<Serializable>>> expectedCalendars = AttributeUtils.getValueAndCastFromMail(mail, DESTINATION_CUSTOM_ATTRIBUTE_NAME, MAP_STRING_CALENDAR_CLASS);

        assertThat(expectedCalendars).hasValueSatisfying(calendars ->
            assertThat(calendars)
                .hasSize(1)
                .hasFieldOrProperty("key1"));
    }

    @Test
    void getMailetInfoShouldReturn() throws MessagingException {
        assertThat(mailet.getMailetInfo()).isEqualTo("Calendar Parser");
    }

    @Test
    void parsingShouldBeLenient() throws Exception {
        FakeMailetConfig mailetConfiguration = FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .setProperty(SOURCE_ATTRIBUTE, SOURCE_CUSTOM_ATTRIBUTE)
            .setProperty(DESTINATION_ATTRIBUTE, DESTINATION_CUSTOM_ATTRIBUTE)
            .build();

        mailet.init(mailetConfiguration);

        Map<String, AttributeValue<byte[]>> attachments = ImmutableMap.<String, AttributeValue<byte[]>>builder()
            .put("key", AttributeValue.of(ClassLoaderUtils.getSystemResourceAsByteArray("ics/ics_with_error.ics")))
            .build();

        Mail mail = FakeMail.builder()
            .name("mail")
            .attribute(makeCustomSourceAttribute((Serializable) attachments))
            .build();

        mailet.service(mail);

        Optional<Map<String, AttributeValue<Serializable>>> expectedCalendars = AttributeUtils.getValueAndCastFromMail(mail, DESTINATION_CUSTOM_ATTRIBUTE_NAME, MAP_STRING_CALENDAR_CLASS);
        assertThat(expectedCalendars).hasValueSatisfying(calendars ->
                assertThat(calendars)
                        .hasSize(1));
    }

    private Attribute makeCustomSourceAttribute(Serializable attachments) {
        return new Attribute(SOURCE_CUSTOM_ATTRIBUTE_NAME, AttributeValue.ofSerializable(attachments));
    }
}
