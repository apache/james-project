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

package org.apache.james.jmap.mailet.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import jakarta.mail.internet.InternetAddress;

import org.junit.jupiter.api.Test;

public class AddressHeaderTest {
    @Test
    void shouldParseAddressWithPersonalName() {
        Optional<InternetAddress> parsed = ContentMatcher.asAddressHeader("Kaël <kael@example.com>")
            .parseFullAddress();

        assertThat(parsed).isPresent();
        assertThat(parsed.get().getAddress()).isEqualTo("kael@example.com");
        assertThat(parsed.get().getPersonal()).isEqualTo("Kaël");
    }

    @Test
    void shouldParseBareAddressWithoutPersonalName() {
        Optional<InternetAddress> parsed = ContentMatcher.asAddressHeader("kael@example.com").parseFullAddress();

        assertThat(parsed).isPresent();
        assertThat(parsed.get().getAddress()).isEqualTo("kael@example.com");
        assertThat(parsed.get().getPersonal()).isNull();
    }

    @Test
    void shouldLenientlyParseMalformedAddressHeader() {
        Optional<InternetAddress> internetAddress = ContentMatcher.asAddressHeader("kael >> Kaël <kael@example.com>")
            .parseFullAddress();

        assertThat(internetAddress).isPresent();
        assertThat(internetAddress.get().getAddress()).isEqualTo("kael@example.com");
        assertThat(internetAddress.get().getPersonal()).isEqualTo("kael >> Kaël");
    }

}
