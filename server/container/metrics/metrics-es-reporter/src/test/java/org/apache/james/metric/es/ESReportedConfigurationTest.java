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

package org.apache.james.metric.es;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.metrics.es.ESReporterConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ESReportedConfigurationTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void builderShouldThrowWhenNotToldIfEnabled() {
        expectedException.expect(IllegalStateException.class);

        ESReporterConfiguration.builder().build();
    }

    @Test
    public void builderShouldThrowIfEnabledWithoutHostAndPort() {
        expectedException.expect(IllegalStateException.class);

        ESReporterConfiguration.builder()
            .enabled()
            .build();
    }

    @Test
    public void builderShouldThrowOnNullHost() {
        expectedException.expect(NullPointerException.class);

        ESReporterConfiguration.builder()
            .onHost(null, 18);
    }

    @Test
    public void builderShouldWorkWhenDisabled() {
        ESReporterConfiguration configuration = ESReporterConfiguration.builder()
            .disabled()
            .build();

        assertThat(configuration.isEnabled()).isFalse();
        assertThat(configuration.getIndex()).isEqualTo(ESReporterConfiguration.DEFAULT_INDEX);
        assertThat(configuration.getPeriodInSecond()).isEqualTo(ESReporterConfiguration.DEFAULT_PERIOD_IN_SECOND);
    }

    @Test
    public void getHostWithPortShouldThrowWhenDisabled() {
        ESReporterConfiguration configuration = ESReporterConfiguration.builder()
            .disabled()
            .build();

        expectedException.expect(IllegalStateException.class);

        configuration.getHostWithPort();
    }

    @Test
    public void builderShouldWorkWhenEnabled() {
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
