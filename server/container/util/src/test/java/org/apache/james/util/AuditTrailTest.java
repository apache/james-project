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

package org.apache.james.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class AuditTrailTest {
    @Test
    void shouldReturnEmptyEntriesByDefault() {
        AuditTrail.Entry auditTrail = AuditTrail.entry();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(auditTrail.username).isEqualTo(Optional.empty());
            softly.assertThat(auditTrail.remoteIP).isEqualTo(Optional.empty());
            softly.assertThat(auditTrail.userAgent).isEqualTo(Optional.empty());
            softly.assertThat(auditTrail.protocol).isEqualTo(Optional.empty());
            softly.assertThat(auditTrail.action).isEqualTo(Optional.empty());
            softly.assertThat(auditTrail.parameters).isEqualTo(Map.of());
        });
    }

    @Test
    void shouldSetEntriesExactly() {
        AuditTrail.Entry auditTrail = AuditTrail.entry()
            .username(() -> "bob@domain.tld")
            .remoteIP(() -> Optional.of(new InetSocketAddress("1.2.3.4", 80)))
            .sessionId(() -> "sessionId")
            .userAgent(() -> "Thunderbird")
            .protocol("IMAP")
            .action("login")
            .parameters(() -> Map.of("key1", "value1",
                "key2", "value2"));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(auditTrail.username).isEqualTo(Optional.of("bob@domain.tld"));
            softly.assertThat(auditTrail.remoteIP).isEqualTo(Optional.of("1.2.3.4"));
            softly.assertThat(auditTrail.sessionId).isEqualTo(Optional.of("sessionId"));
            softly.assertThat(auditTrail.userAgent).isEqualTo(Optional.of("Thunderbird"));
            softly.assertThat(auditTrail.protocol).isEqualTo(Optional.of("IMAP"));
            softly.assertThat(auditTrail.action).isEqualTo(Optional.of("login"));
            softly.assertThat(auditTrail.parameters).isEqualTo(Map.of("key1", "value1",
                "key2", "value2"));
        });
    }

    @Test
    void logShouldNotThrowExceptionUponMissingParams() {
        assertThatCode(() -> AuditTrail.entry()
            .log("message"))
            .doesNotThrowAnyException();
    }

    @Test
    void testLogMessage() {
        Logger auditTrailLogger = (Logger) LoggerFactory.getLogger(AuditTrail.class);
        ListAppender<ILoggingEvent> loggingEvents = new ListAppender<>();
        loggingEvents.start();
        auditTrailLogger.addAppender(loggingEvents);

        AuditTrail.entry()
            .username(() -> "bob@domain.tld")
            .remoteIP(() -> Optional.of(new InetSocketAddress("1.2.3.4", 80)))
            .sessionId(() -> "sessionId")
            .userAgent(() -> "Thunderbird")
            .protocol("IMAP")
            .action("login")
            .parameters(() -> Map.of("key1", "value1",
                "key2", "value2"))
            .log("Authentication via IMAP");

        assertThat(loggingEvents.list).hasSize(1)
            .allSatisfy(loggingEvent -> SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(loggingEvent.getLevel()).isEqualTo(Level.INFO);
                softly.assertThat(loggingEvent.getFormattedMessage()).isEqualTo("Authentication via IMAP");
                softly.assertThat(loggingEvent.getMDCPropertyMap().get("username")).isEqualTo("bob@domain.tld");
                softly.assertThat(loggingEvent.getMDCPropertyMap().get("remoteIP")).isEqualTo("1.2.3.4");
                softly.assertThat(loggingEvent.getMDCPropertyMap().get("sessionId")).isEqualTo("sessionId");
                softly.assertThat(loggingEvent.getMDCPropertyMap().get("userAgent")).isEqualTo("Thunderbird");
                softly.assertThat(loggingEvent.getMDCPropertyMap().get("protocol")).isEqualTo("IMAP");
                softly.assertThat(loggingEvent.getMDCPropertyMap().get("action")).isEqualTo("login");
                softly.assertThat(loggingEvent.getMDCPropertyMap().get("parameters")).contains("key1=value1", "key2=value2");
            }));
    }
}
