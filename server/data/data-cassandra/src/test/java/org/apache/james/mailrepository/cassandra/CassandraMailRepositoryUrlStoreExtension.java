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

package org.apache.james.mailrepository.cassandra;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailrepository.api.MailRepositoryUrlStore;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class CassandraMailRepositoryUrlStoreExtension implements ParameterResolver, BeforeAllCallback, AfterAllCallback, AfterEachCallback, BeforeEachCallback {
    private final DockerCassandraRule cassandra;
    private CassandraCluster cassandraCluster;

    public CassandraMailRepositoryUrlStoreExtension() {
        this.cassandra = new DockerCassandraRule();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        cassandra.start();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        cassandraCluster = CassandraCluster.create(
            new CassandraMailRepositoryUrlModule(),
            cassandra.getHost());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        cassandraCluster.close();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        cassandra.stop();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == MailRepositoryUrlStore.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {


        return new CassandraMailRepositoryUrlStore(
            new UrlsDao(
                cassandraCluster.getConf(),
                CassandraUtils.WITH_DEFAULT_CONFIGURATION));
    }
}
