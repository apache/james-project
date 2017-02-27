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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import nl.jqno.equalsverifier.EqualsVerifier;

public class HostTest {

    private static final int DEFAULT_PORT = 154;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void parseConfStringShouldParseConfWithIpAndPort() {
        //Given
        int expectedPort = 44;
        String expectedIp = "142.145.254.111";
        String ipAndPort = expectedIp + ":" + 44;

        //When
        Host actual = Host.parseConfString(ipAndPort);

        //Then
        assertThat(actual).isEqualTo(new Host(expectedIp, expectedPort));
    }

    @Test
    public void parseConfStringShouldParseConfWithHostanmeAndPort() {
        int expectedPort = 44;
        String host = "host";

        Host actual = Host.parseConfString(host + ":" + expectedPort);

        assertThat(actual).isEqualTo(new Host(host, expectedPort));
    }

    @Test
    public void parseConfStringShouldParseConfWithHostOnlyWhenDefaultPortIsProvided() {
        //Given
        String ipAndPort = "142.145.254.111";
        String expectedIp = "142.145.254.111";

        //When
        Host actual = Host.parseConfString(ipAndPort, DEFAULT_PORT);

        //Then
        assertThat(actual).isEqualTo(new Host(expectedIp, DEFAULT_PORT));
    }

    @Test
    public void parseConfStringShouldFailWhenConfigIsAnEmptyString() {
        expectedException.expect(IllegalArgumentException.class);

        //Given
        String ipAndPort = "";

        //When
        Host.parseConfString(ipAndPort);
    }

    @Test
    public void parseConfStringShouldFailWhenOnlyHostnameAndNoDefaultPort() {
        expectedException.expect(IllegalArgumentException.class);

        //Given
        String hostname = "hostnameOnly";

        //When
        Host.parseConfString(hostname);
    }

    @Test
    public void parseConfStringShouldFailWhenNegativePort() {
        expectedException.expect(IllegalArgumentException.class);

        Host.parseConfString("host:-1");
    }

    @Test
    public void parseConfStringShouldFailWhenZeroPort() {
        expectedException.expect(IllegalArgumentException.class);

        Host.parseConfString("host:0");
    }

    @Test
    public void parseConfStringShouldFailWhenTooHighPort() {
        expectedException.expect(IllegalArgumentException.class);

        Host.parseConfString("host:65536");
    }

    @Test
    public void parseConfStringShouldFailWhenConfigIsANullString() {
        expectedException.expect(NullPointerException.class);

        //Given
        String ipAndPort = null;

        //When
        Host.parseConfString(ipAndPort);
    }


    @Test
    public void parseConfStringShouldFailWhenConfigIsInvalid() {
        expectedException.expect(IllegalArgumentException.class);

        //Given
        String ipAndPort = "10.10.10.10:42:43";

        //When
        Host.parseConfString(ipAndPort);
    }

    @Test
    public void parseHostsShouldParseEmptyString() {
        assertThat(Host.parseHosts(""))
            .isEmpty();
    }

    @Test
    public void parseHostsShouldParseMonoHost() {
        assertThat(Host.parseHosts("localhost:9200"))
            .containsOnly(new Host("localhost", 9200));
    }

    @Test
    public void parseHostsShouldParseMultiHosts() {
        assertThat(Host.parseHosts("localhost:9200,server:9155"))
            .containsOnly(
                new Host("localhost", 9200),
                new Host("server", 9155));
    }

    @Test
    public void parseHostsShouldNotFailOnMultiComma() {
        assertThat(Host.parseHosts("localhost:9200,,server:9155"))
            .containsOnly(
                new Host("localhost", 9200),
                new Host("server", 9155));
    }

    @Test
    public void parseHostsShouldFailOnInvalidHost() {
        expectedException.expect(NumberFormatException.class);

        Host.parseHosts("localhost:invalid,,server:9155");
    }

    @Test
    public void parseHostsShouldSwallowDuplicates() {
        assertThat(Host.parseHosts("localhost:9200,localhost:9200"))
            .containsOnly(
                new Host("localhost", 9200));
    }

    @Test
    public void parseHostsShouldNotSwallowSameAddressDifferentPort() {
        assertThat(Host.parseHosts("localhost:9200,localhost:9155"))
            .containsOnly(
                new Host("localhost", 9200),
                new Host("localhost", 9155));
    }

    @Test
    public void parseHostsShouldNotSwallowSamePortDifferentAddress() {
        assertThat(Host.parseHosts("localhost:9200,abcd:9200"))
            .containsOnly(
                new Host("localhost", 9200),
                new Host("abcd", 9200));
    }

    @Test
    public void parseHostsShouldHandleDefaultPort() {
        int defaultPort = 155;

        assertThat(Host.parseHosts("localhost:9200,abcd", defaultPort))
            .containsOnly(
                new Host("localhost", 9200),
                new Host("abcd", 155));
    }

    @Test
    public void parseHostsShouldThrowOnAbsentPortWhenNoDefaultPort() {
        expectedException.expect(IllegalArgumentException.class);

        Host.parseHosts("localhost:9200,abcd");
    }

    @Test
    public void hostShouldRespectBeanContract() {
        EqualsVerifier.forClass(Host.class).verify();
    }
}
