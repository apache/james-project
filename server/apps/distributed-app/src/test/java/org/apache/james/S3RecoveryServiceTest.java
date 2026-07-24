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

package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;

class S3RecoveryServiceTest {
    @Test
    void parseHeadersShouldExtractEveryDeliveredToRecipient() {
        byte[] headers = ("Delivered-To: alice@domain.tld\r\n"
            + "Delivered-To: bob@domain.tld\r\n"
            + "Subject: hello\r\n"
            + "\r\n").getBytes(StandardCharsets.UTF_8);

        assertThat(S3RecoveryService.parseHeaders(headers).recipients())
            .extracting(MailAddress::asString)
            .containsExactly("alice@domain.tld", "bob@domain.tld");
    }

    @Test
    void parseHeadersShouldExtractDate() {
        byte[] headers = ("Delivered-To: alice@domain.tld\r\n"
            + "Date: Wed, 01 Jan 2020 00:00:00 +0000\r\n"
            + "\r\n").getBytes(StandardCharsets.UTF_8);

        assertThat(S3RecoveryService.parseHeaders(headers).date())
            .hasValueSatisfying(date -> assertThat(date.toInstant()).isEqualTo(Instant.parse("2020-01-01T00:00:00Z")));
    }

    @Test
    void parseHeadersShouldReturnNoRecipientWhenNoDeliveredTo() {
        byte[] headers = ("Subject: hello\r\n\r\n").getBytes(StandardCharsets.UTF_8);

        assertThat(S3RecoveryService.parseHeaders(headers).recipients()).isEmpty();
    }

    @Test
    void parseHeadersShouldReturnNoDateWhenAbsent() {
        byte[] headers = ("Delivered-To: alice@domain.tld\r\n\r\n").getBytes(StandardCharsets.UTF_8);

        assertThat(S3RecoveryService.parseHeaders(headers).date()).isEmpty();
    }

    @Test
    void parseHeadersShouldSkipUnparseableDeliveredToValue() {
        byte[] headers = ("Delivered-To: not-an-address\r\n"
            + "Delivered-To: bob@domain.tld\r\n"
            + "\r\n").getBytes(StandardCharsets.UTF_8);

        assertThat(S3RecoveryService.parseHeaders(headers).recipients())
            .extracting(MailAddress::asString)
            .containsExactly("bob@domain.tld");
    }
}
