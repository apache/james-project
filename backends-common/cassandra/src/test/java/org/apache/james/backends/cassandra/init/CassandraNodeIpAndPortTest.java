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

package org.apache.james.backends.cassandra.init;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CassandraNodeIpAndPortTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void parseConfStringShouldParseConfWithIpAndPort() {
        //Given
        String ipAndPort = "142.145.254.111:44";
        int expectedPort = 44;
        String expectedIp = "142.145.254.111";

        //When
        CassandraNodeIpAndPort actual = CassandraNodeIpAndPort.parseConfString(ipAndPort);

        //Then
        assertThat(actual).isEqualTo(new CassandraNodeIpAndPort(expectedIp, expectedPort));
    }

    @Test
    public void parseConfStringShouldParseConfWithIpOnly() {
        //Given
        String ipAndPort = "142.145.254.111";
        int expectedPort = CassandraNodeIpAndPort.DEFAULT_CASSANDRA_PORT;
        String expectedIp = "142.145.254.111";

        //When
        CassandraNodeIpAndPort actual = CassandraNodeIpAndPort.parseConfString(ipAndPort);

        //Then
        assertThat(actual).isEqualTo(new CassandraNodeIpAndPort(expectedIp, expectedPort));
    }

    @Test
    public void parseConfStringShouldFailWhenConfigIsAnEmptyString() {
        expectedException.expect(IllegalArgumentException.class);

        //Given
        String ipAndPort = "";

        //When
        CassandraNodeIpAndPort.parseConfString(ipAndPort);
    }

    @Test
    public void parseConfStringShouldFailWhenConfigIsANullString() {
        expectedException.expect(NullPointerException.class);

        //Given
        String ipAndPort = null;

        //When
        CassandraNodeIpAndPort.parseConfString(ipAndPort);
    }


    @Test
    public void parseConfStringShouldFailWhenConfigIsInvalid() {
        expectedException.expect(IllegalArgumentException.class);

        //Given
        String ipAndPort = "10.10.10.10:42:43";

        //When
        CassandraNodeIpAndPort.parseConfString(ipAndPort);
    }
}
