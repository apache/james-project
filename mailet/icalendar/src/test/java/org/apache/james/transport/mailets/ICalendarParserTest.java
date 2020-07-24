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

    static final String WRONG_ICAL_VALUE = "anyValue";
    @SuppressWarnings("unchecked")
    static final Class<Map<String, Calendar>> MAP_STRING_CALENDAR_CLASS = (Class<Map<String, Calendar>>) (Object) Map.class;

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

        Map<String, byte[]> attachments = ImmutableMap.<String, byte[]>builder()
            .put("key", RIGHT_ICAL_VALUE.getBytes())
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

        Map<String, byte[]> attachments = ImmutableMap.<String, byte[]>builder()
            .put("key1", WRONG_ICAL_VALUE.getBytes())
            .put("key2", RIGHT_ICAL_VALUE.getBytes())
            .build();
        Mail mail = FakeMail.builder()
            .name("mail")
            .attribute(makeCustomSourceAttribute((Serializable) attachments))
            .build();

        mailet.service(mail);

        Optional<Map<String, Calendar>> expectedCalendars = AttributeUtils.getValueAndCastFromMail(mail, DESTINATION_CUSTOM_ATTRIBUTE_NAME, MAP_STRING_CALENDAR_CLASS);
        Map.Entry<String, Calendar> expectedCalendar = Maps.immutableEntry("key2", new Calendar());

        assertThat(expectedCalendars).hasValueSatisfying(calendars ->
            assertThat(calendars)
                .hasSize(1)
                .containsExactly(expectedCalendar));
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

        Map<String, byte[]> attachments = ImmutableMap.<String, byte[]>builder()
            .put("key", ClassLoaderUtils.getSystemResourceAsByteArray("ics/ics_with_error.ics"))
            .build();

        Mail mail = FakeMail.builder()
            .name("mail")
            .attribute(makeCustomSourceAttribute((Serializable) attachments))
            .build();

        mailet.service(mail);

        Optional<Map<String, Calendar>> expectedCalendars = AttributeUtils.getValueAndCastFromMail(mail, DESTINATION_CUSTOM_ATTRIBUTE_NAME, MAP_STRING_CALENDAR_CLASS);
        assertThat(expectedCalendars).hasValueSatisfying(calendars ->
                assertThat(calendars)
                        .hasSize(1));
    }

    private Attribute makeCustomSourceAttribute(Serializable attachments) {
        return new Attribute(SOURCE_CUSTOM_ATTRIBUTE_NAME, AttributeValue.ofSerializable(attachments));
    }
}
