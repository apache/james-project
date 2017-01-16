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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;

public class ICALToJsonAttributeTest {
    public static final MailAddress SENDER = MailAddressFixture.ANY_AT_JAMES;
    public static final String MEETING_ICS = "\"BEGIN:VCALENDAR\\r\\n" +
        "PRODID:-//Aliasource Groupe LINAGORA//OBM Calendar 3.2.1-rc2//FR\\r\\n" +
        "CALSCALE:GREGORIAN\\r\\n" +
        "X-OBM-TIME:1483703436\\r\\n" +
        "VERSION:2.0\\r\\n" +
        "METHOD:REQUEST\\r\\n" +
        "BEGIN:VEVENT\\r\\n" +
        "CREATED:20170106T115035Z\\r\\n" +
        "LAST-MODIFIED:20170106T115036Z\\r\\n" +
        "DTSTAMP:20170106T115036Z\\r\\n" +
        "DTSTART:20170111T090000Z\\r\\n" +
        "DURATION:PT1H30M\\r\\n" +
        "TRANSP:OPAQUE\\r\\n" +
        "SEQUENCE:0\\r\\n" +
        "SUMMARY:Sprint planning #23\\r\\n" +
        "DESCRIPTION:\\r\\n" +
        "CLASS:PUBLIC\\r\\n" +
        "PRIORITY:5\\r\\n" +
        "ORGANIZER;X-OBM-ID=128;CN=Raphael OUAZANA:MAILTO:ouazana@linagora.com\\r\\n" +
        "X-OBM-DOMAIN:linagora.com\\r\\n" +
        "X-OBM-DOMAIN-UUID:02874f7c-d10e-102f-acda-0015176f7922\\r\\n" +
        "LOCATION:Hangout\\r\\n" +
        "CATEGORIES:\\r\\n" +
        "X-OBM-COLOR:\\r\\n" +
        "UID:f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\\r\\n" +
        "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Matthieu EXT_BAECHLER;PARTSTAT=NEEDS-ACTION;X-OBM-ID=302:MAILTO:baechler@linagora.com\\r\\n" +
        "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Laura ROYET;PARTSTAT=NEEDS-ACTION;X-OBM-ID=723:MAILTO:royet@linagora.com\\r\\n" +
        "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Raphael OUAZANA;PARTSTAT=ACCEPTED;X-OBM-ID=128:MAILTO:ouazana@linagora.com\\r\\n" +
        "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Luc DUZAN;PARTSTAT=NEEDS-ACTION;X-OBM-ID=715:MAILTO:duzan@linagora.com\\r\\n" +
        "ATTENDEE;CUTYPE=RESOURCE;CN=Salle de reunion Lyon;PARTSTAT=ACCEPTED;X-OBM-ID=66:MAILTO:noreply@linagora.com\\r\\n" +
        "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Antoine DUPRAT;PARTSTAT=NEEDS-ACTION;X-OBM-ID=453:MAILTO:duprat@linagora.com\\r\\n" +
        "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=\\\"Benoît TELLIER\\\";PARTSTAT=NEEDS-ACTION;X-OBM-ID=623:MAILTO:tellier@linagora.com\\r\\n" +
        "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Quynh Quynh N NGUYEN;PARTSTAT=NEEDS-ACTION;X-OBM-ID=769:MAILTO:nguyen@linagora.com\\r\\n" +
        "END:VEVENT\\r\\n" +
        "END:VCALENDAR\\r\\n" +
        "\"";
    public static final String SMALL_ICS = "\"BEGIN:VCALENDAR\\r\\n" +
        "PRODID:-//Aliasource Groupe LINAGORA//OBM Calendar 3.2.1-rc2//FR\\r\\n" +
        "CALSCALE:GREGORIAN\\r\\n" +
        "X-OBM-TIME:1483439571\\r\\n" +
        "VERSION:2.0\\r\\n" +
        "METHOD:REQUEST\\r\\n" +
        "BEGIN:VEVENT\\r\\n" +
        "CREATED:20170103T103250Z\\r\\n" +
        "LAST-MODIFIED:20170103T103250Z\\r\\n" +
        "DTSTAMP:20170103T103250Z\\r\\n" +
        "DTSTART:20170120T100000Z\\r\\n" +
        "DURATION:PT30M\\r\\n" +
        "TRANSP:OPAQUE\\r\\n" +
        "SEQUENCE:0\\r\\n" +
        "SUMMARY:Sprint Social #3 Demo\\r\\n" +
        "DESCRIPTION:\\r\\n" +
        "CLASS:PUBLIC\\r\\n" +
        "PRIORITY:5\\r\\n" +
        "ORGANIZER;X-OBM-ID=468;CN=Attendee 1:MAILTO:attendee1@linagora.com\\r\\n" +
        "X-OBM-DOMAIN:linagora.com\\r\\n" +
        "X-OBM-DOMAIN-UUID:02874f7c-d10e-102f-acda-0015176f7922\\r\\n" +
        "LOCATION:hangout\\r\\n" +
        "CATEGORIES:\\r\\n" +
        "X-OBM-COLOR:\\r\\n" +
        "UID:f1514f44bf39311568d64072ac247c17656ceafde3b4b3eba961c8c5184cdc6ee047feb2aab16e43439a608f28671ab7c10e754c301b1e32001ad51dd20eac2fc7af20abf4093bbe\\r\\n" +
        "ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Attendee 2;PARTSTAT=NEEDS-ACTION;X-OBM-ID=348:MAILTO:attendee2@linagora.com\\r\\n" +
        "END:VEVENT\\r\\n" +
        "END:VCALENDAR\\r\\n" +
        "\"";

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
        testee.init(FakeMailetConfig.builder()
            .setProperty(ICALToJsonAttribute.SOURCE_ATTRIBUTE_NAME, source)
            .setProperty(ICALToJsonAttribute.DESTINATION_ATTRIBUTE_NAME, destination)
            .build());

        assertThat(testee.getSourceAttributeName()).isEqualTo(source);
        assertThat(testee.getDestinationAttributeName()).isEqualTo(destination);
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
    public void serviceShouldNotFailOnWrongAttributeParameter() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        ImmutableMap<String, String> wrongParametrizedMap = ImmutableMap.<String, String>builder()
            .put("key", "value")
            .build();
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
    public void serviceShouldFilterMailsWithoutSender() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"));
        ImmutableMap<String, Calendar> icals = ImmutableMap.<String, Calendar>builder()
            .put("key", calendar)
            .build();
        Mail mail = FakeMail.builder()
            .recipient(MailAddressFixture.OTHER_AT_JAMES)
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .build();
        testee.service(mail);

        assertThat(mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME))
            .isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void serviceShouldAttachEmptyListWhenNoRecipient() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"));
        ImmutableMap<String, Calendar> icals = ImmutableMap.<String, Calendar>builder()
            .put("key", calendar)
            .build();
        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .build();
        testee.service(mail);

        assertThat((Map) mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME))
            .isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void serviceShouldAttachJson() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"));
        ImmutableMap<String, Calendar> icals = ImmutableMap.<String, Calendar>builder()
            .put("key", calendar)
            .build();
        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES2;
        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipient(recipient)
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .build();
        testee.service(mail);

        Map<String, byte[]> jsons = (Map<String, byte[]>) mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME);
        assertThat(jsons).hasSize(1);
        assertThatJson(new String(jsons.values().iterator().next(), Charsets.UTF_8))
            .isEqualTo("{" +
                "\"ical\": " + MEETING_ICS +"," +
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
    public void serviceShouldAttachJsonForSeveralRecipient() throws Exception {
        testee.init(FakeMailetConfig.builder().build());

        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"));
        ImmutableMap<String, Calendar> icals = ImmutableMap.<String, Calendar>builder()
            .put("key", calendar)
            .build();
        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipients(MailAddressFixture.OTHER_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2)
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .build();
        testee.service(mail);

        Map<String, byte[]> jsons = (Map<String, byte[]>) mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME);
        assertThat(jsons).hasSize(2);
        List<String> actual = toSortedValueList(jsons);

        assertThatJson(actual.get(0)).isEqualTo("{" +
            "\"ical\": " + MEETING_ICS +"," +
            "\"sender\": \"" + SENDER.asString() + "\"," +
            "\"recipient\": \"" + MailAddressFixture.ANY_AT_JAMES2.asString() + "\"," +
            "\"uid\": \"f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc\"," +
            "\"sequence\": \"0\"," +
            "\"dtstamp\": \"20170106T115036Z\"," +
            "\"method\": \"REQUEST\"," +
            "\"recurrence-id\": null" +
            "}");
        assertThatJson(actual.get(1)).isEqualTo("{" +
            "\"ical\": " + MEETING_ICS +"," +
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

        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"));
        Calendar calendar2 = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting_2.ics"));
        ImmutableMap<String, Calendar> icals = ImmutableMap.<String, Calendar>builder()
            .put("key", calendar)
            .put("key2", calendar2)
            .build();
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipient(recipient)
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .build();
        testee.service(mail);

        Map<String, byte[]> jsons = (Map<String, byte[]>) mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME);
        assertThat(jsons).hasSize(2);
        List<String> actual = toSortedValueList(jsons);

        assertThatJson(actual.get(0)).isEqualTo("{" +
            "\"ical\": " + SMALL_ICS +"," +
            "\"sender\": \"" + SENDER.asString() + "\"," +
            "\"recipient\": \"" + recipient.asString() + "\"," +
            "\"uid\": \"f1514f44bf39311568d64072ac247c17656ceafde3b4b3eba961c8c5184cdc6ee047feb2aab16e43439a608f28671ab7c10e754c301b1e32001ad51dd20eac2fc7af20abf4093bbe\"," +
            "\"sequence\": \"0\"," +
            "\"dtstamp\": \"20170103T103250Z\"," +
            "\"method\": \"REQUEST\"," +
            "\"recurrence-id\": null" +
            "}");
        assertThatJson(actual.get(1)).isEqualTo("{" +
            "\"ical\": " + MEETING_ICS +"," +
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

        Calendar calendar = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"));
        Calendar calendar2 = new CalendarBuilder().build(ClassLoader.getSystemResourceAsStream("ics/meeting_without_uid.ics"));
        ImmutableMap<String, Calendar> icals = ImmutableMap.<String, Calendar>builder()
            .put("key", calendar)
            .put("key2", calendar2)
            .build();
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        Mail mail = FakeMail.builder()
            .sender(SENDER)
            .recipient(recipient)
            .attribute(ICALToJsonAttribute.DEFAULT_SOURCE_ATTRIBUTE_NAME, icals)
            .build();
        testee.service(mail);

        Map<String, byte[]> jsons = (Map<String, byte[]>) mail.getAttribute(ICALToJsonAttribute.DEFAULT_DESTINATION_ATTRIBUTE_NAME);
        assertThat(jsons).hasSize(1);
        List<String> actual = toSortedValueList(jsons);

        assertThatJson(actual.get(0)).isEqualTo("{" +
            "\"ical\": " + MEETING_ICS + "," +
            "\"sender\": \"" + SENDER.asString() + "\"," +
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
                .map(bytes -> new String(bytes, Charsets.UTF_8))
                .sorted()
                .collect(Collectors.toList());
    }
}
