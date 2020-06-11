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

import java.io.ByteArrayInputStream;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.mailets.ICal4JConfigurator;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.mailet.base.MailAddressFixture;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import nl.jqno.equalsverifier.EqualsVerifier;

public class ICALAttributeDTOTest {

    @BeforeClass
    public static void setUpIcal4J() {
        ICal4JConfigurator.configure();
    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void buildShouldWork() throws Exception {
        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));

        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress sender = MailAddressFixture.OTHER_AT_JAMES;
        ICALAttributeDTO ical = ICALAttributeDTO.builder()
            .from(calendar, ics)
            .sender(sender)
            .recipient(recipient)
            .replyTo(sender);

        assertThat(ical.getRecipient()).isEqualTo(recipient.asString());
        assertThat(ical.getSender()).isEqualTo(sender.asString());
        assertThat(ical.getUid())
            .contains("f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7" +
                "c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc");
        assertThat(ical.getMethod()).contains("REQUEST");
        assertThat(ical.getRecurrenceId()).isEmpty();
        assertThat(ical.getDtstamp()).contains("20170106T115036Z");
        assertThat(ical.getSequence()).isEqualTo("0");
        assertThat(ical.getIcal()).isEqualTo(new String(ics, "UTF-8"));
    }

    @Test
    public void equalsAndHashCodeShouldBeWellImplemented() {
        EqualsVerifier.forClass(ICALAttributeDTO.class).verify();
    }

    @Test
    public void buildShouldThrowOnCalendarWithoutDtstamp() throws Exception {
        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting_without_dtstamp.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));

        expectedException.expect(IllegalStateException.class);

        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress sender = MailAddressFixture.OTHER_AT_JAMES;
        ICALAttributeDTO.builder()
            .from(calendar, ics)
            .sender(sender)
            .recipient(recipient)
            .replyTo(sender);
    }

    @Test
    public void buildShouldThrowOnCalendarWithoutUid() throws Exception {
        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting_without_uid.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));

        expectedException.expect(IllegalStateException.class);

        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress sender = MailAddressFixture.OTHER_AT_JAMES;
        ICALAttributeDTO.builder()
            .from(calendar, ics)
            .sender(sender)
            .recipient(recipient)
            .replyTo(sender);
    }

    @Test
    public void buildShouldThrowOnCalendarWithoutMethod() throws Exception {
        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting_without_method.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));

        expectedException.expect(IllegalStateException.class);

        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress sender = MailAddressFixture.OTHER_AT_JAMES;
        ICALAttributeDTO.builder()
            .from(calendar, ics)
            .sender(sender)
            .recipient(recipient)
            .replyTo(sender);
    }

    @Test
    public void buildShouldSetDefaultValueWhenCalendarWithoutSequence() throws Exception {
        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting_without_sequence.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));

        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress sender = MailAddressFixture.OTHER_AT_JAMES;
        ICALAttributeDTO ical = ICALAttributeDTO.builder()
            .from(calendar, ics)
            .sender(sender)
            .recipient(recipient)
            .replyTo(sender);

        assertThat(ical.getSequence()).isEqualTo(ICALAttributeDTO.DEFAULT_SEQUENCE_VALUE);
    }
}
