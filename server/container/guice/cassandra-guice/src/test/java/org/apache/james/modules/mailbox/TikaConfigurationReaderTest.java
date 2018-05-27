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

package org.apache.james.modules.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.time.Duration;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.junit.Test;

public class TikaConfigurationReaderTest {

    @Test
    public void readTikaConfigurationShouldAcceptMandatoryValues() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader(
            "tika.host=172.0.0.5\n" +
                "tika.port=889\n" +
                "tika.timeoutInMillis=500\n"));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(500)
                    .cacheWeightInBytes(100L * 1024L *1024L)
                    .cacheEvictionPeriod(Duration.ofDays(1))
                    .build());
    }

    @Test
    public void readTikaConfigurationShouldReturnDefaultOnMissingHost() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader(
                "tika.port=889\n" +
                "tika.timeoutInMillis=500\n"));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .host("127.0.0.1")
                    .port(889)
                    .timeoutInMillis(500)
                    .build());
    }

    @Test
    public void readTikaConfigurationShouldReturnDefaultOnMissingPort() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader(
            "tika.host=172.0.0.5\n" +
                "tika.timeoutInMillis=500\n"));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .host("172.0.0.5")
                    .port(9998)
                    .timeoutInMillis(500)
                    .build());
    }

    @Test
    public void readTikaConfigurationShouldReturnDefaultOnMissingTimeout() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader(
            "tika.host=172.0.0.5\n" +
                "tika.port=889\n"));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(30 * 1000)
                    .build());
    }


    @Test
    public void readTikaConfigurationShouldParseUnitForCacheEvictionPeriod() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader(
            "tika.host=172.0.0.5\n" +
                "tika.port=889\n" +
                "tika.timeoutInMillis=500\n" +
                "tika.cache.eviction.period=2H"));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(500)
                    .cacheEvictionPeriod(Duration.ofHours(2))
                    .build());
    }

    @Test
    public void readTikaConfigurationShouldDefaultToSecondWhenMissingUnitForCacheEvitionPeriod() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader(
            "tika.host=172.0.0.5\n" +
                "tika.port=889\n" +
                "tika.timeoutInMillis=500\n" +
                "tika.cache.eviction.period=3600"));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(500)
                    .cacheEvictionPeriod(Duration.ofHours(1))
                    .build());
    }

    @Test
    public void readTikaConfigurationShouldParseUnitForCacheWeightMax() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader(
            "tika.host=172.0.0.5\n" +
                "tika.port=889\n" +
                "tika.timeoutInMillis=500\n" +
                "tika.cache.weight.max=200M"));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(500)
                    .cacheWeightInBytes(200L * 1024L * 1024L)
                    .build());
    }

    @Test
    public void readTikaConfigurationShouldDefaultToByteAsSizeUnit() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader(
            "tika.host=172.0.0.5\n" +
                "tika.port=889\n" +
                "tika.timeoutInMillis=500\n" +
                "tika.cache.weight.max=1520000"));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(500)
                    .cacheWeightInBytes(1520000)
                    .build());
    }


}