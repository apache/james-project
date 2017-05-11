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

package org.apache.james;

import java.util.Arrays;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.modules.mailbox.ElasticSearchConfiguration;
import org.apache.james.util.streams.SwarmGenericContainer;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.shaded.com.github.dockerjava.api.model.ExposedPort;
import org.testcontainers.shaded.com.github.dockerjava.api.model.Ports.Binding;

import com.google.inject.Module;


public class DockerElasticSearchRule implements GuiceModuleTestRule {

    private static final int ELASTIC_SEARCH_PORT = 9300;
    public static final int ELASTIC_SEARCH_HTTP_PORT = 9200;

    private static boolean isBindingToEveryThing(Binding binding) {
        String bindingIp = binding.getHostIp();
        return bindingIp == null || bindingIp.equals("0.0.0.0");
    }

    public PropertiesConfiguration getElasticSearchConfigurationForDocker() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        configuration.addProperty("elasticsearch.masterHost", getIp());
        configuration.addProperty("elasticsearch.port", ELASTIC_SEARCH_PORT);

        configuration.addProperty("elasticsearch.nb.shards", 1);
        configuration.addProperty("elasticsearch.nb.replica", 0);
        configuration.addProperty("elasticsearch.retryConnection.maxRetries", 7);
        configuration.addProperty("elasticsearch.retryConnection.minDelay", 3000);
        configuration.addProperty("elasticsearch.indexAttachments", false);
        configuration.addProperty("elasticsearch.http.host", getIp());
        configuration.addProperty("elasticsearch.http.port", ELASTIC_SEARCH_HTTP_PORT);
        configuration.addProperty("elasticsearch.metrics.reports.enabled", true);
        configuration.addProperty("elasticsearch.metrics.reports.period", 30);
        configuration.addProperty("elasticsearch.metrics.reports.index", "james-metrics");

        return configuration;
    }

    private SwarmGenericContainer elasticSearchContainer = new SwarmGenericContainer("elasticsearch:2.2.2");

    @Override
    public Statement apply(Statement base, Description description) {
        return elasticSearchContainer.apply(base, description);
    }

    @Override
    public void await() {
    }

    @Override
    public Module getModule() {
        return (binder) -> binder.bind(ElasticSearchConfiguration.class).toInstance(this::getElasticSearchConfigurationForDocker);
    }

    public String getIp() {
        return elasticSearchContainer.getIp();
    }

    public int getBindingPort() {
        Binding[] bindings =  elasticSearchContainer
                .getContainerInfo()
                .getNetworkSettings()
                .getPorts()
                .getBindings()
                .get(ExposedPort.tcp(ELASTIC_SEARCH_PORT));

        return Integer.valueOf(
                Arrays.stream(bindings)
                    .filter(DockerElasticSearchRule::isBindingToEveryThing)
                    .map(binding -> binding.getHostPortSpec())
                    .findFirst().get());
    }

    public SwarmGenericContainer getElasticSearchContainer() {
        return elasticSearchContainer;
    }
}
