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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.component.VEvent;

class ICalendarParserTimeZoneTest {
    private static final String ICS = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//Apache James//Timezone test//EN
        BEGIN:VTIMEZONE
        TZID:{TZID}
        BEGIN:DAYLIGHT
        DTSTART:19700329T020000
        TZOFFSETFROM:+0100
        TZOFFSETTO:+0200
        RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU
        END:DAYLIGHT
        BEGIN:STANDARD
        DTSTART:19701025T030000
        TZOFFSETFROM:+0200
        TZOFFSETTO:+0100
        RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU
        END:STANDARD
        END:VTIMEZONE
        BEGIN:VEVENT
        UID:timezone-test
        DTSTAMP:20261026T000000Z
        DTSTART;TZID={TZID}:{DATE}T114500
        DTEND;TZID={TZID}:{DATE}T123000
        END:VEVENT
        END:VCALENDAR
        """;

    private final ICalendarParser mailet = new ICalendarParser();

    @BeforeEach
    void setUp() throws Exception {
        mailet.init(FakeMailetConfig.builder().mailetName("ICalendarParser").build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Europe/Paris", "Romance Standard Time"})
    void shouldNotExhaustTimezonePoolWhileRetainingParsedCalendars(String tzId) throws Exception {
        List<Calendar> calendars = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            calendars.add(parse(ICS.replace("{TZID}", tzId).replace("{DATE}", "20261026")));
        }

        assertThat(calendars).hasSize(2000)
            .allSatisfy(calendar -> assertThat(start(calendar)).isEqualTo(Instant.parse("2026-10-26T10:45:00Z")));
    }

    @ParameterizedTest
    @CsvSource({"20260715, 2026-07-15T09:45:00Z", "20261026, 2026-10-26T10:45:00Z"})
    void shouldResolveSummerAndWinterOffsets(String date, String expected) throws Exception {
        Calendar calendar = parse(ICS.replace("{TZID}", "Europe/Paris").replace("{DATE}", date));

        assertThat(start(calendar)).isEqualTo(Instant.parse(expected));
    }

    @Test
    void shouldResolveCustomTimezoneFromItsDefinition() throws Exception {
        Calendar calendar = parse(ICS.replace("{TZID}", "Custom James Time").replace("{DATE}", "20260715"));

        assertThat(start(calendar)).isEqualTo(Instant.parse("2026-07-15T09:45:00Z"));
    }

    @Test
    void shouldRejectACalendarWithTooManyCustomTimezones() throws Exception {
        // Without recurrence rules, the recurrence budget of the calendar does not stop the registrations
        String timezones = IntStream.range(0, 1600)
            .mapToObj(i -> fixedOffsetTimezone("X-" + i))
            .collect(Collectors.joining());

        assertThat(parsedCalendars(calendarWith(timezones, "X-0"))).isEmpty();
        assertThat(start(parse(ICS.replace("{TZID}", "Custom James Time").replace("{DATE}", "20260715"))))
            .isEqualTo(Instant.parse("2026-07-15T09:45:00Z"));
    }

    @Test
    void shouldAcceptTheMaximumOfCustomTimezones() throws Exception {
        String timezones = IntStream.range(0, ICal4JTimeZoneRegistry.MAX_CUSTOM_TIMEZONES)
            .mapToObj(i -> timezone("X-" + i))
            .collect(Collectors.joining());

        assertThat(start(parse(calendarWith(timezones, "X-1")))).isEqualTo(Instant.parse("2026-07-15T09:45:00Z"));
    }

    @Test
    void shouldRegisterARepeatedCustomTimezoneOnce() throws Exception {
        String timezones = timezone("Custom James Time").repeat(1600);
        List<Calendar> calendars = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            calendars.add(parse(calendarWith(timezones, "Custom James Time")));
        }

        assertThat(calendars)
            .allSatisfy(calendar -> assertThat(start(calendar)).isEqualTo(Instant.parse("2026-07-15T09:45:00Z")));
    }

    @Test
    void shouldBoundTheCustomTimezonesOfAllTheCalendarsOfAMail() throws Exception {
        Map<String, AttributeValue<?>> attachments = IntStream.range(0, 80)
            .boxed()
            .collect(Collectors.toMap(i -> "calendar-" + i, i -> AttributeValue.of(calendarWith(IntStream.range(0, 20)
                .mapToObj(j -> fixedOffsetTimezone("X-" + i + "-" + j))
                .collect(Collectors.joining()), "X-" + i + "-0").getBytes(StandardCharsets.UTF_8))));

        assertThat(parsedCalendars(attachments)).hasSize(1);
        assertThat(start(parse(ICS.replace("{TZID}", "Custom James Time").replace("{DATE}", "20260715"))))
            .isEqualTo(Instant.parse("2026-07-15T09:45:00Z"));
    }

    @Test
    void shouldAcceptACustomTimezoneWhoseObservancesStartInUtc() throws Exception {
        String timezone = timezone("Custom UTC Start")
            .replace("DTSTART:19700329T020000", "DTSTART:19700329T010000Z")
            .replace("DTSTART:19701025T030000", "DTSTART:19701025T010000Z");

        assertThat(start(parse(calendarWith(timezone, "Custom UTC Start")))).isEqualTo(Instant.parse("2026-07-15T09:45:00Z"));
    }

    private static String timezone(String tzId) {
        return ICS.substring(ICS.indexOf("BEGIN:VTIMEZONE"), ICS.indexOf("BEGIN:VEVENT")).replace("{TZID}", tzId);
    }

    private static String fixedOffsetTimezone(String tzId) {
        return """
            BEGIN:VTIMEZONE
            TZID:%s
            BEGIN:STANDARD
            DTSTART:19700101T000000
            TZOFFSETFROM:+0100
            TZOFFSETTO:+0100
            END:STANDARD
            END:VTIMEZONE
            """.formatted(tzId);
    }

    private static String calendarWith(String timezones, String usedTzId) {
        String event = ICS.substring(ICS.indexOf("BEGIN:VEVENT")).replace("{TZID}", usedTzId).replace("{DATE}", "20260715");
        return ICS.substring(0, ICS.indexOf("BEGIN:VTIMEZONE")) + timezones + event;
    }

    private Map<?, ?> parsedCalendars(String ics) throws Exception {
        return parsedCalendars(Map.of("calendar", AttributeValue.of(ics.getBytes(StandardCharsets.UTF_8))));
    }

    private Map<?, ?> parsedCalendars(Map<String, AttributeValue<?>> attachments) throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("timezone-test")
            .attribute(new Attribute(AttributeName.of(ICalendarParser.SOURCE_ATTRIBUTE_PARAMETER_DEFAULT_VALUE),
                AttributeValue.of(attachments)))
            .build();

        mailet.service(mail);

        return (Map<?, ?>) mail.getAttribute(AttributeName.of(ICalendarParser.DESTINATION_ATTRIBUTE_PARAMETER_DEFAULT_VALUE))
            .orElseThrow().getValue().value();
    }

    private Calendar parse(String ics) throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("timezone-test")
            .attribute(new Attribute(AttributeName.of(ICalendarParser.SOURCE_ATTRIBUTE_PARAMETER_DEFAULT_VALUE),
                AttributeValue.of(Map.of("calendar", AttributeValue.of(ics.getBytes(StandardCharsets.UTF_8))))))
            .build();

        mailet.service(mail);

        Map<?, ?> calendars = (Map<?, ?>) mail.getAttribute(AttributeName.of(ICalendarParser.DESTINATION_ATTRIBUTE_PARAMETER_DEFAULT_VALUE))
            .orElseThrow().getValue().value();
        assertThat(calendars).hasSize(1);
        return (Calendar) ((AttributeValue<?>) calendars.get("calendar")).getValue();
    }

    private Instant start(Calendar calendar) {
        VEvent event = calendar.<VEvent>getComponent(Component.VEVENT).orElseThrow();
        return ((ZonedDateTime) event.getDateTimeStart().getDate()).toInstant();
    }
}
