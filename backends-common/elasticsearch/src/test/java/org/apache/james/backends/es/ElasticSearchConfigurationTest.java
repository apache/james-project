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

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.util.Host;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ElasticSearchConfigurationTest {

    @Test
    public void elasticSearchConfigurationShouldRespectBeanContract() {
        EqualsVerifier.forClass(ElasticSearchConfiguration.class)
            .verify();
    }

    @Test
    public void getNbReplicaShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 36;
        configuration.addProperty("elasticsearch.nb.replica", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getNbReplica())
            .isEqualTo(value);
    }

    @Test
    public void getNbReplicaShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getNbReplica())
            .isEqualTo(ElasticSearchConfiguration.DEFAULT_NB_REPLICA);
    }

    @Test
    public void getWaitForActiveShardsShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 36;
        configuration.addProperty("elasticsearch.index.waitForActiveShards", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getWaitForActiveShards())
            .isEqualTo(value);
    }

    @Test
    public void getWaitForActiveShardsShouldReturnConfiguredValueWhenZero() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 0;
        configuration.addProperty("elasticsearch.index.waitForActiveShards", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getWaitForActiveShards())
            .isEqualTo(value);
    }

    @Test
    public void getWaitForActiveShardsShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        int expectedValue = 1;
        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getWaitForActiveShards())
            .isEqualTo(expectedValue);
    }

    @Test
    public void getNbShardsShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 36;
        configuration.addProperty("elasticsearch.nb.shards", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getNbShards())
            .isEqualTo(value);
    }

    @Test
    public void getNbShardsShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getNbShards())
            .isEqualTo(ElasticSearchConfiguration.DEFAULT_NB_SHARDS);
    }

    @Test
    public void getMaxRetriesShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 36;
        configuration.addProperty("elasticsearch.retryConnection.maxRetries", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getMaxRetries())
            .isEqualTo(value);
    }

    @Test
    public void getMaxRetriesShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getMaxRetries())
            .isEqualTo(ElasticSearchConfiguration.DEFAULT_CONNECTION_MAX_RETRIES);
    }

    @Test
    public void getMinDelayShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 36;
        configuration.addProperty("elasticsearch.retryConnection.minDelay", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getMinDelay())
            .isEqualTo(value);
    }

    @Test
    public void getMinDelayShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getMinDelay())
            .isEqualTo(ElasticSearchConfiguration.DEFAULT_CONNECTION_MIN_DELAY);
    }

    @Test
    public void getHostsShouldReturnConfiguredHostsWhenNoPort() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String hostname = "myHost";
        configuration.addProperty("elasticsearch.hosts", hostname);

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getHosts())
            .containsOnly(Host.from(hostname, ElasticSearchConfiguration.DEFAULT_PORT));
    }

    @Test
    public void getHostsShouldReturnConfiguredHostsWhenListIsUsed() throws ConfigurationException {
        String hostname = "myHost";
        String hostname2 = "myOtherHost";
        int port = 2154;
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.setListDelimiterHandler(new DefaultListDelimiterHandler(','));
        configuration.addProperty("elasticsearch.hosts", hostname + "," + hostname2 + ":" + port);

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getHosts())
            .containsOnly(Host.from(hostname, ElasticSearchConfiguration.DEFAULT_PORT),
                Host.from(hostname2, port));
    }

    @Test
    public void getHostsShouldReturnConfiguredHosts() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String hostname = "myHost";
        int port = 2154;
        configuration.addProperty("elasticsearch.hosts", hostname + ":" + port);

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getHosts())
            .containsOnly(Host.from(hostname, port));
    }

    @Test
    public void getHostsShouldReturnConfiguredMasterHost() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String hostname = "myHost";
        configuration.addProperty("elasticsearch.masterHost", hostname);
        int port = 9200;
        configuration.addProperty("elasticsearch.port", port);

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getHosts())
            .containsOnly(Host.from(hostname, port));
    }

    @Test
    public void validateHostsConfigurationOptionsShouldThrowWhenNoHostSpecify() {
        assertThatThrownBy(() ->
            ElasticSearchConfiguration.validateHostsConfigurationOptions(
                Optional.empty(),
                Optional.empty(),
                ImmutableList.of()))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("You should specify either (" + ElasticSearchConfiguration.ELASTICSEARCH_MASTER_HOST +
                " and " + ElasticSearchConfiguration.ELASTICSEARCH_PORT +
                ") or " + ElasticSearchConfiguration.ELASTICSEARCH_HOSTS);
    }

    @Test
    public void validateHostsConfigurationOptionsShouldThrowWhenMonoAndMultiHostSpecified() {
        assertThatThrownBy(() ->
            ElasticSearchConfiguration.validateHostsConfigurationOptions(
                Optional.of("localhost"),
                Optional.of(9200),
                ImmutableList.of("localhost:9200")))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("You should choose between mono host set up and " + ElasticSearchConfiguration.ELASTICSEARCH_HOSTS);
    }

    @Test
    public void validateHostsConfigurationOptionsShouldThrowWhenMonoHostWithoutPort() {
        assertThatThrownBy(() ->
            ElasticSearchConfiguration.validateHostsConfigurationOptions(
                Optional.of("localhost"),
                Optional.empty(),
                ImmutableList.of()))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage(ElasticSearchConfiguration.ELASTICSEARCH_MASTER_HOST +
                " and " + ElasticSearchConfiguration.ELASTICSEARCH_PORT + " should be specified together");
    }

    @Test
    public void validateHostsConfigurationOptionsShouldThrowWhenMonoHostWithoutAddress() {
        assertThatThrownBy(() ->
        ElasticSearchConfiguration.validateHostsConfigurationOptions(
            Optional.empty(),
            Optional.of(9200),
            ImmutableList.of()))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage(ElasticSearchConfiguration.ELASTICSEARCH_MASTER_HOST + " and " +
            ElasticSearchConfiguration.ELASTICSEARCH_PORT + " should be specified together");
    }

    @Test
    public void validateHostsConfigurationOptionsShouldAcceptMonoHostConfiguration() throws Exception {
        ElasticSearchConfiguration.validateHostsConfigurationOptions(
            Optional.of("localhost"),
            Optional.of(9200),
            ImmutableList.of());
    }

    @Test
    public void validateHostsConfigurationOptionsShouldAcceptMultiHostConfiguration() throws Exception {
        ElasticSearchConfiguration.validateHostsConfigurationOptions(
            Optional.empty(),
            Optional.empty(),
            ImmutableList.of("localhost:9200"));
    }

    @Test
    public void nbReplicaShouldThrowWhenNegative() {
        assertThatThrownBy(() ->
                ElasticSearchConfiguration.builder()
                        .nbReplica(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void waitForActiveShardsShouldThrowWhenNegative() {
        assertThatThrownBy(() ->
            ElasticSearchConfiguration.builder()
                .waitForActiveShards(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void nbShardsShouldThrowWhenNegative() {
        assertThatThrownBy(() ->
                ElasticSearchConfiguration.builder()
                        .nbShards(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void nbShardsShouldThrowWhenZero() {
        assertThatThrownBy(() ->
                ElasticSearchConfiguration.builder()
                        .nbShards(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
