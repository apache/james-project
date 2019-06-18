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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import net.fortuna.ical4j.model.Calendar;

public class ICalendarParserTest {
    private static final String DESTINATION_ATTRIBUTE = "destinationAttribute";
    private static final String SOURCE_ATTRIBUTE = "sourceAttribute";

    private static final String DESTINATION_CUSTOM_ATTRIBUTE = "ics.dest.attribute";
    private static final AttributeName DESTINATION_CUSTOM_ATTRIBUTE_NAME = AttributeName.of(DESTINATION_CUSTOM_ATTRIBUTE);
    private static final String SOURCE_CUSTOM_ATTRIBUTE = "ics.source.attribute";
    private static final AttributeName SOURCE_CUSTOM_ATTRIBUTE_NAME = AttributeName.of(SOURCE_CUSTOM_ATTRIBUTE);

    private static final String RIGHT_ICAL_VALUE = "BEGIN:VCALENDAR\n" +
        "END:VCALENDAR";

    private static final String WRONG_ICAL_VALUE = "anyValue";
    @SuppressWarnings("unchecked")
    public static final Class<Map<String, Calendar>> MAP_STRING_CALENDAR_CLASS = (Class<Map<String, Calendar>>) (Object) Map.class;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private ICalendarParser mailet = new ICalendarParser();

    @Test
    public void initShouldSetSourceAttributeWhenAbsent() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .build());

        assertThat(mailet.getSourceAttributeName().asString()).isEqualTo(ICalendarParser.SOURCE_ATTRIBUTE_PARAMETER_DEFAULT_VALUE);
    }

    @Test
    public void initShouldSetDestinationAttributeWhenAbsent() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .build());

        assertThat(mailet.getDestinationAttributeName().asString()).isEqualTo(ICalendarParser.DESTINATION_ATTRIBUTE_PARAMETER_DEFAULT_VALUE);
    }

    @Test
    public void initShouldSetSourceAttributeWhenPresent() throws Exception {
        String sourceAttribute = "sourceAttribute";
        mailet.init(FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .setProperty(ICalendarParser.SOURCE_ATTRIBUTE_PARAMETER_NAME, sourceAttribute)
            .build());

        assertThat(mailet.getSourceAttributeName().asString()).isEqualTo(sourceAttribute);
    }

    @Test
    public void initShouldSetDestinationAttributeWhenPresent() throws Exception {
        String destinationAttribute = "destinationAttribute";
        mailet.init(FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .setProperty(ICalendarParser.DESTINATION_ATTRIBUTE_PARAMETER_NAME, destinationAttribute)
            .build());

        assertThat(mailet.getDestinationAttributeName().asString()).isEqualTo(destinationAttribute);
    }

    @Test
    public void initShouldThrowOnEmptySourceAttribute() throws Exception {
        expectedException.expect(MessagingException.class);

        mailet.init(FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .setProperty(ICalendarParser.SOURCE_ATTRIBUTE_PARAMETER_NAME, "")
            .build());
    }

    @Test
    public void initShouldThrowOnEmptyDestinationAttribute() throws Exception {
        expectedException.expect(MessagingException.class);

        mailet.init(FakeMailetConfig.builder()
            .mailetName("ICalendarParser")
            .setProperty(ICalendarParser.DESTINATION_ATTRIBUTE_PARAMETER_NAME, "")
            .build());
    }

    @Test
    public void serviceShouldNotSetCalendarDataIntoMailAttributeWhenNoSourceAttribute() throws Exception {
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
    public void serviceShouldSetEmptyCalendarDataIntoMailAttributeWhenEmptyICSAttachments() throws Exception {
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
    public void serviceShouldNotSetCalendarDataIntoMailAttributeWhenSourceAttributeIsNotAMap() throws Exception {
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
    public void serviceShouldReturnRightMapOfCalendarWhenRightAttachments() throws Exception {
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
    public void serviceShouldFilterResultWhenErrorParsing() throws Exception {
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
    public void getMailetInfoShouldReturn() throws MessagingException {
        assertThat(mailet.getMailetInfo()).isEqualTo("Calendar Parser");
    }

    @Test
    public void parsingShouldBeLenient() throws Exception {
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
