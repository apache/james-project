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

package org.apache.james.transport.mailets.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.mailet.MailAddress;
import org.apache.mailet.base.MailAddressFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import nl.jqno.equalsverifier.EqualsVerifier;

public class ICALTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void buildShouldFailWhenNoCalendar() throws Exception {
        expectedException.expect(NullPointerException.class);

        ICAL.builder()
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .build();
    }

    @Test
    public void buildShouldFailWhenNoSender() throws Exception {
        expectedException.expect(NullPointerException.class);

        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"));

        ICAL.builder()
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .from(calendar)
            .build();
    }

    @Test
    public void buildShouldFailWhenNoRecipient() throws Exception {
        expectedException.expect(NullPointerException.class);

        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"));

        ICAL.builder()
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .from(calendar)
            .build();
    }


    @Test
    public void buildShouldWork() throws Exception {
        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"));

        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress sender = MailAddressFixture.OTHER_AT_JAMES;
        ICAL ical = ICAL.builder()
            .recipient(recipient)
            .sender(sender)
            .from(calendar)
            .build();

        assertThat(ical.getRecipient()).isEqualTo(recipient.asString());
        assertThat(ical.getSender()).isEqualTo(sender.asString());
        assertThat(ical.getUid())
            .contains("f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7" +
                "c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc");
        assertThat(ical.getMethod()).contains("REQUEST");
        assertThat(ical.getRecurrenceId()).isEmpty();
        assertThat(ical.getDtstamp()).contains("20170106T115036Z");
        assertThat(ical.getSequence()).isEqualTo("0");
        assertThat(ical.getIcal()).isEqualTo("BEGIN:VCALENDAR\r\n" +
            "PRODID:-//Aliasource Groupe LINAGORA//OBM Calendar 3.2.1-rc2//FR\r\n" +
            "CALSCALE:GREGORIAN\r\n" +
            "X-OBM-TIME:1483703436\r\n" +
            "VERSION:2.0\r\n" +
            "METHOD:REQUEST\r\n" +
            "BEGIN:VEVENT\r\n" +
            "CREATED:20170106T115035Z\r\n" +
            "LAST-MODIFIED:20170106T115036Z\r\n" +
            "DTSTAMP:20170106T115036Z\r\n" +
            "DTSTART:20170111T090000Z\r\n" +
            "DURATION:PT1H30M\r\n" +
            "TRANSP:OPAQUE\r\n" +
            "SEQUENCE:0\r\n" +
            "SUMMARY:Sprint planning #23\r\n" +
            "DESCRIPTION:\r\n" +
            "CLASS:PUBLIC\r\n" +
            "PRIORITY:5\r\n" +
            "ORGANIZER;X-OBM-ID=128;CN=Raphael OUAZANA:MAILTO:ouazana@linagora.com\r\n" +
            "X-OBM-DOMAIN:linagora.com\r\n" +
            "X-OBM-DOMAIN-UUID:02874f7c-d10e-102f-acda-0015176f7922\r\n" +
            "LOCATION:Hangout\r\n" +
            "CATEGORIES:\r\n" +
            "X-OBM-COLOR:\r\n" +
            "UID:f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\r\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Matthieu EXT_BAECHLER;PARTSTAT=NEEDS-ACTION;X-OBM-ID=302:MAILTO:baechler@linagora.com\r\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Laura ROYET;PARTSTAT=NEEDS-ACTION;X-OBM-ID=723:MAILTO:royet@linagora.com\r\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Raphael OUAZANA;PARTSTAT=ACCEPTED;X-OBM-ID=128:MAILTO:ouazana@linagora.com\r\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Luc DUZAN;PARTSTAT=NEEDS-ACTION;X-OBM-ID=715:MAILTO:duzan@linagora.com\r\n" +
            "ATTENDEE;CUTYPE=RESOURCE;CN=Salle de reunion Lyon;PARTSTAT=ACCEPTED;X-OBM-ID=66:MAILTO:noreply@linagora.com\r\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Antoine DUPRAT;PARTSTAT=NEEDS-ACTION;X-OBM-ID=453:MAILTO:duprat@linagora.com\r\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=\"Benoît TELLIER\";PARTSTAT=NEEDS-ACTION;X-OBM-ID=623:MAILTO:tellier@linagora.com\r\n" +
            "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Quynh Quynh N NGUYEN;PARTSTAT=NEEDS-ACTION;X-OBM-ID=769:MAILTO:nguyen@linagora.com\r\n" +
            "END:VEVENT\r\n" +
            "END:VCALENDAR\r\n");
    }

    @Test
    public void equalsAndHashCodeShouldBeWellImplemented() {
        EqualsVerifier.forClass(ICAL.class).verify();
    }

    @Test
    public void buildShouldThrowOnCalendarWithoutDtstamp() throws Exception {
        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting_without_dtstamp.ics"));

        expectedException.expect(IllegalStateException.class);

        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress sender = MailAddressFixture.OTHER_AT_JAMES;
        ICAL.builder()
            .recipient(recipient)
            .sender(sender)
            .from(calendar)
            .build();
    }

    @Test
    public void buildShouldThrowOnCalendarWithoutUid() throws Exception {
        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting_without_uid.ics"));

        expectedException.expect(IllegalStateException.class);

        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress sender = MailAddressFixture.OTHER_AT_JAMES;
        ICAL.builder()
            .recipient(recipient)
            .sender(sender)
            .from(calendar)
            .build();
    }

    @Test
    public void buildShouldThrowOnCalendarWithoutMethod() throws Exception {
        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting_without_method.ics"));

        expectedException.expect(IllegalStateException.class);

        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress sender = MailAddressFixture.OTHER_AT_JAMES;
        ICAL.builder()
            .recipient(recipient)
            .sender(sender)
            .from(calendar)
            .build();
    }

    @Test
    public void buildShouldSetDefaultValueWhenCalendarWithoutSequence() throws Exception {
        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting_without_sequence.ics"));

        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress sender = MailAddressFixture.OTHER_AT_JAMES;
        ICAL ical = ICAL.builder()
            .recipient(recipient)
            .sender(sender)
            .from(calendar)
            .build();

        assertThat(ical.getSequence()).isEqualTo(ICAL.DEFAULT_SEQUENCE_VALUE);
    }
}
