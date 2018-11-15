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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.elasticsearch.common.settings.Settings;
import org.junit.Test;

public class ClientProviderImplTest {

    @Test
    public void fromHostsStringShouldThrowOnNullString() {
        assertThatThrownBy(() -> ClientProviderImpl.fromHostsString(null, Optional.empty()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromHostsStringShouldThrowOnEmptyString() {
        assertThatThrownBy(() -> ClientProviderImpl.fromHostsString("", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void forHostShouldThrowOnNullHost() {
        assertThatThrownBy(() -> ClientProviderImpl.forHost(null, 9200, Optional.empty()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void forHostShouldThrowOnEmptyHost() {
        assertThatThrownBy(() -> ClientProviderImpl.forHost("", 9200, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void forHostShouldThrowOnNegativePort() {
        assertThatThrownBy(() -> ClientProviderImpl.forHost("localhost", -1, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void forHostShouldThrowOnZeroPort() {
        assertThatThrownBy(() -> ClientProviderImpl.forHost("localhost", 0, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void forHostShouldThrowOnTooBigPort() {
        assertThatThrownBy(() -> ClientProviderImpl.forHost("localhost", 65536, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromHostsStringShouldEmptyAddress() {
        assertThatThrownBy(() -> ClientProviderImpl.fromHostsString(":9200", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromHostsStringShouldThrowOnAbsentPort() {
        assertThatThrownBy(() -> ClientProviderImpl.fromHostsString("localhost", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromHostsStringShouldThrowWhenTooMuchParts() {
        assertThatThrownBy(() -> ClientProviderImpl.fromHostsString("localhost:9200:9200", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromHostsStringShouldThrowOnEmptyPort() {
        assertThatThrownBy(() -> ClientProviderImpl.fromHostsString("localhost:", Optional.empty()))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    public void fromHostsStringShouldThrowOnInvalidPort() {
        assertThatThrownBy(() -> ClientProviderImpl.fromHostsString("localhost:invalid", Optional.empty()))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    public void fromHostsStringShouldThrowOnNegativePort() {
        assertThatThrownBy(() -> ClientProviderImpl.fromHostsString("localhost:-1", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromHostsStringShouldThrowOnZeroPort() {
        assertThatThrownBy(() -> ClientProviderImpl.fromHostsString("localhost:0", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromHostsStringShouldThrowOnTooBigPort() {
        assertThatThrownBy(() -> ClientProviderImpl.fromHostsString("localhost:65536", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromHostsStringShouldThrowIfOneHostIsInvalid() {
        assertThatThrownBy(() -> ClientProviderImpl.fromHostsString("localhost:9200,localhost", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void settingsShouldBeEmptyWhenClusterNameIsEmpty() {
        ClientProviderImpl clientProvider = ClientProviderImpl.fromHostsString("localhost:9200", Optional.empty());

        assertThat(clientProvider.settings()).isEqualTo(Settings.EMPTY);
    }

    @Test
    public void settingsShouldContainClusterNameSettingWhenClusterNameIsGiven() {
        String clusterName = "myClusterName";
        ClientProviderImpl clientProvider = ClientProviderImpl.fromHostsString("localhost:9200", Optional.of(clusterName));

        assertThat(clientProvider.settings().get("cluster.name")).isEqualTo(clusterName);
    }
}
