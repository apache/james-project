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

package org.apache.james.crowdsec;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.crowdsec.model.CrowdsecDecision;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CrowdsecUtilsTest {
    private static CrowdsecDecision decision(String scope, String value) {
        return decision(scope, value, CrowdsecDecision.BAN);
    }

    private static CrowdsecDecision decision(String scope, String value, String type) {
        return CrowdsecDecision.builder()
            .scope(scope)
            .value(value)
            .type(type)
            .build();
    }

    @ParameterizedTest
    @CsvSource({
        "Ip, 1.2.3.4, 1.2.3.4",
        "Ip, 2001:db8::1, 2001:db8:0:0:0:0:0:1",
        "Range, 1.2.3.0/24, 1.2.3.255",
        "Range, 2001:db8::/32, 2001:db8:0:0:0:0:0:1"
    })
    void isBannedShouldMatchTheClientAddress(String scope, String value, String ip) {
        assertThat(CrowdsecUtils.isBanned(decision(scope, value), ip)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "Ip, 1.2.3.4, 11.2.3.4",
        "Ip, 1.2.3.4, 1.2.3.40",
        "Range, 1.2.3.0/24, 1.2.4.1",
        "Range, 2001:db8::/32, 2001:db9:0:0:0:0:0:1",
        "Range, 1.2.3.0/24, 0:0:0:0:0:0:0:1",
        "Range, 2001:db8::/64, 10.0.0.1",
        "Range, 10.0.0.0/8, 2001:db8:0:0:0:0:0:2",
        "Range, not-a-range, 10.0.0.1",
        "Range, 10.0.0.0/x, 10.0.0.1",
        "Ip, not-an-ip, 10.0.0.1"
    })
    void isBannedShouldNotMatchOtherAddresses(String scope, String value, String ip) {
        assertThat(CrowdsecUtils.isBanned(decision(scope, value), ip)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"captcha", "throttle"})
    void isBannedShouldIgnoreDecisionsOtherThanBans(String type) {
        assertThat(CrowdsecUtils.isBanned(decision("Ip", "1.2.3.4", type), "1.2.3.4")).isFalse();
    }
}
