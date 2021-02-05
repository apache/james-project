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

package org.apache.james.metric.es.v7;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.metrics.es.v7.ESReporterConfiguration;
import org.junit.jupiter.api.Test;

class ESReportedConfigurationTest {

    @Test
    void builderShouldThrowWhenNotToldIfEnabled() {
        assertThatThrownBy(() -> ESReporterConfiguration.builder().build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builderShouldThrowIfEnabledWithoutHostAndPort() {
        assertThatThrownBy(() -> ESReporterConfiguration.builder()
                .enabled()
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builderShouldThrowOnNullHost() {
        assertThatThrownBy(() -> ESReporterConfiguration.builder()
                .onHost(null, 18))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderShouldWorkWhenDisabled() {
        ESReporterConfiguration configuration = ESReporterConfiguration.builder()
            .disabled()
            .build();

        assertThat(configuration.isEnabled()).isFalse();
        assertThat(configuration.getIndex()).isEqualTo(ESReporterConfiguration.DEFAULT_INDEX);
        assertThat(configuration.getPeriodInSecond()).isEqualTo(ESReporterConfiguration.DEFAULT_PERIOD_IN_SECOND);
    }

    @Test
    void getHostWithPortShouldThrowWhenDisabled() {
        ESReporterConfiguration configuration = ESReporterConfiguration.builder()
            .disabled()
            .build();

        assertThatThrownBy(() -> configuration.getHostWithPort())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builderShouldWorkWhenEnabled() {
        int port = 14;
        String host = "host";
        ESReporterConfiguration configuration = ESReporterConfiguration.builder()
            .enabled()
            .onHost(host, port)
            .build();

        assertThat(configuration.isEnabled()).isTrue();
        assertThat(configuration.getHostWithPort()).isEqualTo(host + ":" + port);
        assertThat(configuration.getIndex()).isEqualTo(ESReporterConfiguration.DEFAULT_INDEX);
        assertThat(configuration.getPeriodInSecond()).isEqualTo(ESReporterConfiguration.DEFAULT_PERIOD_IN_SECOND);
    }

}
