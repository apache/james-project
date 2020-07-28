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
package org.apache.james.transport.mailets.redirect;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.server.core.MailImpl;
import org.junit.jupiter.api.Test;

public class MailModifierTest {

    @Test
    void buildShouldThrowWhenMailetIsNull() {
        assertThatThrownBy(() -> MailModifier.builder().build())
            .isInstanceOf(NullPointerException.class)
            .hasMessage("'mailet' is mandatory");
    }

    @Test
    void buildShouldThrowWhenMailIsNull() {
        assertThatThrownBy(() -> MailModifier.builder()
                .mailet(mock(RedirectNotify.class))
                .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessage("'mail' is mandatory");
    }

    @Test
    void buildShouldThrowWhenDNSIsNull() {
        assertThatThrownBy(() -> MailModifier.builder()
                .mailet(mock(RedirectNotify.class))
                .mail(mock(MailImpl.class))
                .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessage("'dns' is mandatory");
    }

    @Test
    void buildShouldWorkWhenEverythingProvided() {
        MailModifier.builder()
            .mailet(mock(RedirectNotify.class))
            .mail(mock(MailImpl.class))
            .dns(mock(DNSService.class))
            .build();
    }
}
