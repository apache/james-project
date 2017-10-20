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
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.text.RandomStringGenerator;
import org.apache.james.backends.cassandra.init.CassandraSessionConfiguration;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.containers.GenericContainer;

import com.google.inject.Module;


public class DockerCassandraRule implements GuiceModuleTestRule {

    private org.apache.james.backends.cassandra.DockerCassandraRule cassandraContainer = new org.apache.james.backends.cassandra.DockerCassandraRule();
    
    public PropertiesConfiguration getCassandraConfigurationForDocker(String keyspace) {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        configuration.addProperty("cassandra.nodes", cassandraContainer.getIp() + ":" + cassandraContainer.getBindingPort());
        configuration.addProperty("cassandra.keyspace", keyspace);
        configuration.addProperty("cassandra.replication.factor", 1);
        configuration.addProperty("cassandra.retryConnection.maxRetries", 20);
        configuration.addProperty("cassandra.retryConnection.minDelay", 5000);

        return configuration;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return cassandraContainer.apply(base, description);
    }

    @Override
    public void await() {
    }

    @Override
    public Module getModule() {
        String keyspace = new RandomStringGenerator.Builder().withinRange('a', 'z').build().generate(12);
        return (binder) -> binder.bind(CassandraSessionConfiguration.class).toInstance(() -> getCassandraConfigurationForDocker(keyspace));
    }

    public String getIp() {
        return cassandraContainer.getIp();
    }

    public Integer getMappedPort(int originalPort) {
        return cassandraContainer.getBindingPort();
    }

    public void start() {
        cassandraContainer.start();
    }

    public void stop() {
        cassandraContainer.stop();
    }

    public GenericContainer<?> getRawContainer() {
        return cassandraContainer.getRawContainer();
    }

    public void pause() {
        cassandraContainer.pause();
    }

    public void unpause() {
        cassandraContainer.unpause();
    }

}
