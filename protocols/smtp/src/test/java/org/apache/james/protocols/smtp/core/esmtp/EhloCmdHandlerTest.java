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

package org.apache.james.protocols.smtp.core.esmtp;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EhloCmdHandlerTest {

    private final EhloCmdHandler handler = new EhloCmdHandler(new RecordingMetricFactory());

    @ParameterizedTest
    @ValueSource(strings = {
        // Standard domain names
        "example.com",
        "mail.example.com",
        "sub.domain.example.org",
        // RFC 1123: labels may start with a digit
        "mx-ll-110-164-x-x.2s1n",
        "1mail.example.com",
        "123.example.com",
        // Pure alphanumeric (single label, no dot)
        "localhost",
        "mailserver",
        // IPv4
        "192.168.1.1",
        "10.0.0.1",
        // IPv6 with brackets
        "[::1]",
        "[2001:db8::1]",
    })
    void isValidShouldAccept(String argument) {
        assertThat(handler.isValid(argument)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // Empty or blank
        "",
        // Labels starting or ending with hyphen
        "-example.com",
        "example-.com",
        "example.-com",
        "example.com-",
        // Empty label (double dot)
        "example..com",
        // Invalid characters
        "exam ple.com",
        "example.com!",
    })
    void isValidShouldReject(String argument) {
        assertThat(handler.isValid(argument)).isFalse();
    }
}
