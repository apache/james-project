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
import org.apache.james.modules.mailbox.CassandraSessionConfiguration;
import org.apache.james.util.streams.SwarmGenericContainer;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.google.inject.Module;


public class DockerCassandraRule implements GuiceModuleTestRule {

    private static final int CASSANDRA_PORT = 9042;

    private static boolean isBindingToEveryThing(Ports.Binding binding) {
        String bindingIp = binding.getHostIp();
        return bindingIp == null || bindingIp.equals("0.0.0.0");
    }

    public PropertiesConfiguration getCassandraConfigurationForDocker() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        configuration.addProperty("cassandra.nodes", getIp() + ":" + CASSANDRA_PORT);
        configuration.addProperty("cassandra.keyspace", "apache_james");
        configuration.addProperty("cassandra.replication.factor", 1);
        configuration.addProperty("cassandra.retryConnection.maxRetries", 20);
        configuration.addProperty("cassandra.retryConnection.minDelay", 5000);

        return configuration;
    }

    private SwarmGenericContainer cassandraContainer = new SwarmGenericContainer("cassandra:2.2")
        .withExposedPorts(CASSANDRA_PORT);

    @Override
    public Statement apply(Statement base, Description description) {
        return cassandraContainer.apply(base, description);
    }

    @Override
    public void await() {
    }

    @Override
    public Module getModule() {
        return (binder) -> binder.bind(CassandraSessionConfiguration.class).toInstance(this::getCassandraConfigurationForDocker);
    }

    public String getIp() {
        return cassandraContainer.getIp();
    }

    public int getBindingPort() {
        Ports.Binding[] bindings =  cassandraContainer
                .getContainerInfo()
                .getNetworkSettings()
                .getPorts()
                .getBindings()
                .get(ExposedPort.tcp(CASSANDRA_PORT));

        return Integer.valueOf(
                Arrays.stream(bindings)
                    .filter(DockerCassandraRule::isBindingToEveryThing)
                    .map(Ports.Binding::getHostPortSpec)
                    .findFirst().get());
    }

    public SwarmGenericContainer getCassandraContainer() {
        return cassandraContainer;
    }
}
