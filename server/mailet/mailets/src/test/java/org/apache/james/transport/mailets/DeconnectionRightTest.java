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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeconnectionRightTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "2024-07-01T07:00:00.00Z",
        "2024-07-01T10:15:30.00Z",
        "2024-07-01T18:00:00.00Z",
        "2024-07-01T18:00:00.01Z",
        "2024-07-01T20:00:00.00Z"})
    // 2024-07-01 => monday
    void noDelaywithUTC(String dateTime) throws Exception {
        Clock clock = Clock.fixed(Instant.parse(dateTime), ZoneId.of("UTC"));
        DeconnectionRight testee = new DeconnectionRight(clock);
        FakeMailContext mailetContext = FakeMailContext.defaultContext();
        testee.init(FakeMailetConfig.builder()
                .mailetContext(mailetContext)
                .setProperty("zoneId", "UTC")
                .setProperty("workDayStart", "07:00:00")
                .setProperty("workDayEnd", "20:00:00")
            .build());

        FakeMail mail = FakeMail.builder().name("aMail").state(Mail.DEFAULT).build();
        testee.service(mail);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mail.getState()).isEqualTo(Mail.DEFAULT);
            softly.assertThat(mailetContext.getSentMails()).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "2024-07-01T20:00:00.01Z",
        "2024-07-01T21:00:00.01Z",
        "2024-07-02T03:00:00.01Z",
        "2024-07-02T06:59:59.99Z"
    })
    // 2024-07-01 => monday
    // 2024-07-01 => tuesday
    void tomorrowWithUTC(String dateTime) throws Exception {
        Instant now = Instant.parse(dateTime);
        ZoneId zone = ZoneId.of("UTC");
        Clock clock = Clock.fixed(now, zone);

        DeconnectionRight testee = new DeconnectionRight(clock);
        FakeMailContext mailetContext = FakeMailContext.defaultContext();
        testee.init(FakeMailetConfig.builder()
                .mailetContext(mailetContext)
                .setProperty("zoneId", "UTC")
                .setProperty("workDayStart", "07:00:00")
                .setProperty("workDayEnd", "20:00:00")
            .build());

        FakeMail mail = FakeMail.builder().name("aMail").state(Mail.DEFAULT).build();
        testee.service(mail);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mail.getState()).isEqualTo(Mail.GHOST);
            softly.assertThat(mailetContext.getSentMails()).hasSize(1);
            softly.assertThat(mailetContext.getSentMails().get(0).getDelay().get()).isEqualTo(new FakeMailContext.Delay(
                Duration.between(ZonedDateTime.ofInstant(now, zone),
                    ZonedDateTime.ofInstant(Instant.parse("2024-07-02T07:00:00.00Z"), zone))
                        .toSeconds(), TimeUnit.SECONDS
            ));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "2024-07-05T20:00:00.01Z",
        "2024-07-05T21:00:00.01Z",
        "2024-07-06T03:00:00.01Z",
        "2024-07-07T03:00:00.01Z",
        "2024-07-08T06:59:59.99Z"
    })
    // 2024-07-01 => monday
    void afterWeekendWithUTC(String dateTime) throws Exception {
        Instant now = Instant.parse(dateTime);
        ZoneId zone = ZoneId.of("UTC");
        Clock clock = Clock.fixed(now, zone);

        DeconnectionRight testee = new DeconnectionRight(clock);
        FakeMailContext mailetContext = FakeMailContext.defaultContext();
        testee.init(FakeMailetConfig.builder()
                .mailetContext(mailetContext)
                .setProperty("zoneId", "UTC")
                .setProperty("workDayStart", "07:00:00")
                .setProperty("workDayEnd", "20:00:00")
            .build());

        FakeMail mail = FakeMail.builder().name("aMail").state(Mail.DEFAULT).build();
        testee.service(mail);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mail.getState()).isEqualTo(Mail.GHOST);
            softly.assertThat(mailetContext.getSentMails()).hasSize(1);
            softly.assertThat(mailetContext.getSentMails().get(0).getDelay().get()).isEqualTo(new FakeMailContext.Delay(
                Duration.between(ZonedDateTime.ofInstant(now, zone),
                    ZonedDateTime.ofInstant(Instant.parse("2024-07-08T07:00:00.00Z"), zone))
                        .toSeconds(), TimeUnit.SECONDS
            ));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "2024-07-01T05:00:00.00Z",
        "2024-07-01T10:15:30.00Z",
        "2024-07-01T18:00:00.00Z"})
    // 2024-07-01 => monday
    void noDelaywithUTCPlus2(String dateTime) throws Exception {
        Clock clock = Clock.fixed(Instant.parse(dateTime), ZoneId.of("UTC"));
        DeconnectionRight testee = new DeconnectionRight(clock);
        FakeMailContext mailetContext = FakeMailContext.defaultContext();
        testee.init(FakeMailetConfig.builder()
            .mailetContext(mailetContext)
            .setProperty("zoneId", "Europe/Paris")
            .setProperty("workDayStart", "07:00:00")
            .setProperty("workDayEnd", "20:00:00")
            .build());

        FakeMail mail = FakeMail.builder().name("aMail").state(Mail.DEFAULT).build();
        testee.service(mail);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mail.getState()).isEqualTo(Mail.DEFAULT);
            softly.assertThat(mailetContext.getSentMails()).isEmpty();
        });
    }

    @Test// 2024-03-30 => saturday before time change
    void handleTimeChange() throws Exception {
        Instant now = Instant.parse("2024-03-30T05:00:00.00Z");
        ZoneId zone = ZoneId.of("UTC");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        DeconnectionRight testee = new DeconnectionRight(clock);
        FakeMailContext mailetContext = FakeMailContext.defaultContext();
        testee.init(FakeMailetConfig.builder()
            .mailetContext(mailetContext)
            .setProperty("zoneId", "Europe/Paris")
            .setProperty("workDayStart", "07:00:00")
            .setProperty("workDayEnd", "20:00:00")
            .build());

        FakeMail mail = FakeMail.builder().name("aMail").state(Mail.DEFAULT).build();
        testee.service(mail);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mail.getState()).isEqualTo(Mail.GHOST);
            softly.assertThat(mailetContext.getSentMails()).hasSize(1);
            softly.assertThat(mailetContext.getSentMails().get(0).getDelay().get()).isEqualTo(new FakeMailContext.Delay(
                Duration.between(ZonedDateTime.ofInstant(now, zone),
                        // 7am on next monday besides time change
                        ZonedDateTime.ofInstant(Instant.parse("2024-04-01T05:00:00.00Z"), ZoneId.of("UTC")))
                    .toSeconds(), TimeUnit.SECONDS
            ));
        });
    }
}