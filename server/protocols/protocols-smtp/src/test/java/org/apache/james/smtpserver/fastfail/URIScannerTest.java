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

package org.apache.james.smtpserver.fastfail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

class URIScannerTest {
    @Test
    void scanContentForHostsShouldBeFastOnALongRunWithoutAt() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
            assertThat(URIScanner.scanContentForHosts("a".repeat(1_000_000))).isEmpty());
    }

    @Test
    void domainFromHostShouldBeFastOnALongHost() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
            assertThat(URIScanner.domainFromHost("a".repeat(300_000))).isNull());
    }

    @Test
    void scanContentForDomainsShouldBeFastOnALongUrlHost() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
            assertThat(URIScanner.scanContentForDomains(new HashSet<>(), "http://" + "a".repeat(300_000))).isEmpty());
    }

    @Test
    void scanContentForHostsShouldFindAddressesAndUrls() {
        assertThat(URIScanner.scanContentForHosts("Write to first.last@sub.mail.example.com or see http://www.other.example/page"))
            .containsExactlyInAnyOrder("sub.mail.example.com", "www.other.example");
    }
}
