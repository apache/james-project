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

package org.apache.james.backends.es;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.es.ClientProviderImpl.Host;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ClientProviderImplTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void fromHostsStringShouldThrowOnNullString() {
        expectedException.expect(NullPointerException.class);

        ClientProviderImpl.fromHostsString(null);
    }

    @Test
    public void fromHostsStringShouldThrowOnEmptyString() {
        expectedException.expect(IllegalArgumentException.class);

        ClientProviderImpl.fromHostsString("");
    }

    @Test
    public void forHostShouldThrowOnNullHost() {
        expectedException.expect(NullPointerException.class);

        ClientProviderImpl.forHost(null, 9200);
    }

    @Test
    public void forHostShouldThrowOnEmptyHost() {
        expectedException.expect(IllegalArgumentException.class);

        ClientProviderImpl.forHost("", 9200);
    }

    @Test
    public void forHostShouldThrowOnNegativePort() {
        expectedException.expect(IllegalArgumentException.class);

        ClientProviderImpl.forHost("localhost", -1);
    }

    @Test
    public void forHostShouldThrowOnZeroPort() {
        expectedException.expect(IllegalArgumentException.class);

        ClientProviderImpl.forHost("localhost", 0);
    }

    @Test
    public void forHostShouldThrowOnTooBigPort() {
        expectedException.expect(IllegalArgumentException.class);

        ClientProviderImpl.forHost("localhost", 65536);
    }

    @Test
    public void fromHostsStringShouldEmptyAddress() {
        expectedException.expect(IllegalArgumentException.class);

        ClientProviderImpl.fromHostsString(":9200");
    }

    @Test
    public void fromHostsStringShouldThrowOnAbsentPort() {
        expectedException.expect(IllegalArgumentException.class);

        ClientProviderImpl.fromHostsString("localhost");
    }

    @Test
    public void fromHostsStringShouldThrowWhenTooMuchParts() {
        expectedException.expect(IllegalArgumentException.class);

        ClientProviderImpl.fromHostsString("localhost:9200:9200");
    }

    @Test
    public void fromHostsStringShouldThrowOnEmptyPort() {
        expectedException.expect(NumberFormatException.class);

        ClientProviderImpl.fromHostsString("localhost:");
    }

    @Test
    public void fromHostsStringShouldThrowOnInvalidPort() {
        expectedException.expect(NumberFormatException.class);

        ClientProviderImpl.fromHostsString("localhost:invalid");
    }

    @Test
    public void fromHostsStringShouldThrowOnNegativePort() {
        expectedException.expect(IllegalArgumentException.class);

        ClientProviderImpl.fromHostsString("localhost:-1");
    }

    @Test
    public void fromHostsStringShouldThrowOnZeroPort() {
        expectedException.expect(IllegalArgumentException.class);

        ClientProviderImpl.fromHostsString("localhost:0");
    }

    @Test
    public void fromHostsStringShouldThrowOnTooBigPort() {
        expectedException.expect(IllegalArgumentException.class);

        ClientProviderImpl.fromHostsString("localhost:65536");
    }

    @Test
    public void fromHostsStringShouldThrowIfOneHostIsInvalid() {
        expectedException.expect(IllegalArgumentException.class);

        ClientProviderImpl.fromHostsString("localhost:9200,localhost");
    }

    @Test
    public void parseHostsShouldParseMonoHost() {
        assertThat(ClientProviderImpl.parseHosts("localhost:9200"))
            .containsOnly(new Host("localhost", 9200));
    }

    @Test
    public void parseHostsShouldParseMultiHosts() {
        assertThat(ClientProviderImpl.parseHosts("localhost:9200,server:9155"))
            .containsOnly(
                new Host("localhost", 9200),
                new Host("server", 9155));
    }

    @Test
    public void parseHostsShouldSwallowDuplicates() {
        assertThat(ClientProviderImpl.parseHosts("localhost:9200,localhost:9200"))
            .containsOnly(
                new Host("localhost", 9200));
    }

    @Test
    public void parseHostsShouldNotSwallowSameAddressDifferentPort() {
        assertThat(ClientProviderImpl.parseHosts("localhost:9200,localhost:9155"))
            .containsOnly(
                new Host("localhost", 9200),
                new Host("localhost", 9155));
    }



    @Test
    public void parseHostsShouldNotSwallowSamePortDifferentAddress() {
        assertThat(ClientProviderImpl.parseHosts("localhost:9200,abcd:9200"))
            .containsOnly(
                new Host("localhost", 9200),
                new Host("abcd", 9200));
    }


    @Test
    public void hostShouldRespectBeanContract() {
        EqualsVerifier.forClass(ClientProviderImpl.Host.class).verify();
    }
}
