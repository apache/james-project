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
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import nl.jqno.equalsverifier.EqualsVerifier;

class ICALAttributeDTOTest {

    @BeforeAll
    static void setUpIcal4J() {
        ICal4JConfigurator.configure();
    }

    @Test
    void buildShouldWork() throws Exception {
        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));

        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress sender = MailAddressFixture.OTHER_AT_JAMES;
        ICALAttributeDTO ical = ICALAttributeDTO.builder()
            .from(calendar, ics)
            .sender(sender)
            .recipient(recipient)
            .replyTo(sender);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            softly.assertThat(ical.getRecipient()).isEqualTo(recipient.asString());
            softly.assertThat(ical.getSender()).isEqualTo(sender.asString());
            softly.assertThat(ical.getUid())
                .contains("f1514f44bf39311568d640727cff54e819573448d09d2e5677987ff29caa01a9e047feb2aab16e43439a608f28671ab7" +
                    "c10e754ce92be513f8e04ae9ff15e65a9819cf285a6962bc");
            softly.assertThat(ical.getMethod()).contains("REQUEST");
            softly.assertThat(ical.getRecurrenceId()).isEmpty();
            softly.assertThat(ical.getDtstamp()).contains("20170106T115036Z");
            softly.assertThat(ical.getSequence()).isEqualTo("0");
            softly.assertThat(ical.getIcal()).isEqualTo(new String(ics, "UTF-8"));
        }));
    }

    @Test
    void equalsAndHashCodeShouldBeWellImplemented() {
        EqualsVerifier.forClass(ICALAttributeDTO.class).verify();
    }

    @Test
    void dtoShouldNotFailUponMissingUid() throws Exception {
        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting_without_uid.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));

        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress sender = MailAddressFixture.OTHER_AT_JAMES;
        ICALAttributeDTO ical = ICALAttributeDTO.builder()
            .from(calendar, ics)
            .sender(sender)
            .recipient(recipient)
            .replyTo(sender);

        assertThat(ical.getUid()).isEmpty();
    }

    @Test
    void dtoShouldNotFailUponMissingMethod() throws Exception {
        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting_without_method.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));

        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress sender = MailAddressFixture.OTHER_AT_JAMES;
        ICALAttributeDTO ical = ICALAttributeDTO.builder()
            .from(calendar, ics)
            .sender(sender)
            .recipient(recipient)
            .replyTo(sender);

        assertThat(ical.getMethod()).isEmpty();
    }

    @Test
    void dtoShouldNotFailUponMissingDtStamp() throws Exception {
        byte[] ics = ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting_without_dtstamp.ics");
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(ics));

        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        MailAddress sender = MailAddressFixture.OTHER_AT_JAMES;
        ICALAttributeDTO ical = ICALAttributeDTO.builder()
            .from(calendar, ics)
            .sender(sender)
            .recipient(recipient)
            .replyTo(sender);

        assertThat(ical.getDtstamp()).isEmpty();
    }

    @Test
    void buildShouldSetDefaultValueWhenCalendarWithoutSequence() throws Exception {
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
