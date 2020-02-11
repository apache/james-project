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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class HostTest {

    private static final int DEFAULT_PORT = 154;

    @Test
    void parseConfStringShouldParseConfWithIpAndPort() {
        int expectedPort = 44;
        String expectedIp = "142.145.254.111";
        String ipAndPort = expectedIp + ":" + 44;

        Host actual = Host.parseConfString(ipAndPort);

        assertThat(actual).isEqualTo(new Host(expectedIp, expectedPort));
    }

    @Test
    void parseConfStringShouldParseConfWithHostanmeAndPort() {
        int expectedPort = 44;
        String host = "host";

        Host actual = Host.parseConfString(host + ":" + expectedPort);

        assertThat(actual).isEqualTo(new Host(host, expectedPort));
    }

    @Test
    void parseConfStringShouldParseConfWithHostOnlyWhenDefaultPortIsProvided() {
        String ipAndPort = "142.145.254.111";
        String expectedIp = "142.145.254.111";

        Host actual = Host.parseConfString(ipAndPort, DEFAULT_PORT);

        assertThat(actual).isEqualTo(new Host(expectedIp, DEFAULT_PORT));
    }

    @Test
    void parseConfStringShouldFailWhenConfigIsAnEmptyString() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Host.parseConfString(""));
    }

    @Test
    void parseConfStringShouldFailWhenOnlyHostnameAndNoDefaultPort() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Host.parseConfString("hostnameOnly"));
    }

    @Test
    void parseConfStringShouldFailWhenNegativePort() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Host.parseConfString("host:-1"));
    }

    @Test
    void parseConfStringShouldFailWhenZeroPort() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Host.parseConfString("host:0"));
    }

    @Test
    void parseConfStringShouldFailWhenTooHighPort() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Host.parseConfString("host:65536"));
    }

    @Test
    void parseConfStringShouldFailWhenConfigIsANullString() {
        assertThatNullPointerException()
            .isThrownBy(() -> Host.parseConfString(null));
    }


    @Test
    void parseConfStringShouldFailWhenConfigIsInvalid() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Host.parseConfString("10.10.10.10:42:43"));
    }

    @Test
    void parseHostsShouldParseEmptyString() {
        assertThat(Host.parseHosts(""))
            .isEmpty();
    }

    @Test
    void parseHostsShouldParseMonoHost() {
        assertThat(Host.parseHosts("localhost:9200"))
            .containsOnly(new Host("localhost", 9200));
    }

    @Test
    void parseHostsShouldParseMultiHosts() {
        assertThat(Host.parseHosts("localhost:9200,server:9155"))
            .containsOnly(
                new Host("localhost", 9200),
                new Host("server", 9155));
    }

    @Test
    void parseHostsShouldNotFailOnMultiComma() {
        assertThat(Host.parseHosts("localhost:9200,,server:9155"))
            .containsOnly(
                new Host("localhost", 9200),
                new Host("server", 9155));
    }

    @Test
    void parseHostsShouldFailOnInvalidHost() {
        assertThatThrownBy(() -> Host.parseHosts("localhost:invalid,,server:9155"))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void parseHostsShouldSwallowDuplicates() {
        assertThat(Host.parseHosts("localhost:9200,localhost:9200"))
            .containsOnly(
                new Host("localhost", 9200));
    }

    @Test
    void parseHostsShouldNotSwallowSameAddressDifferentPort() {
        assertThat(Host.parseHosts("localhost:9200,localhost:9155"))
            .containsOnly(
                new Host("localhost", 9200),
                new Host("localhost", 9155));
    }

    @Test
    void parseHostsShouldNotSwallowSamePortDifferentAddress() {
        assertThat(Host.parseHosts("localhost:9200,abcd:9200"))
            .containsOnly(
                new Host("localhost", 9200),
                new Host("abcd", 9200));
    }

    @Test
    void parseHostsShouldHandleDefaultPort() {
        int defaultPort = 155;

        assertThat(Host.parseHosts("localhost:9200,abcd", defaultPort))
            .containsOnly(
                new Host("localhost", 9200),
                new Host("abcd", 155));
    }

    @Test
    void parseHostsShouldThrowOnAbsentPortWhenNoDefaultPort() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Host.parseHosts("localhost:9200,abcd"));
    }

    @Test
    void hostShouldRespectBeanContract() {
        EqualsVerifier.forClass(Host.class).verify();
    }
}
