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

package org.apache.james.modules;

import java.io.IOException;

import javax.inject.Singleton;

import org.apache.james.CleanupTasksPerformer;
import org.apache.james.backends.es.DockerElasticSearch;
import org.apache.james.backends.es.ElasticSearchConfiguration;
import org.apache.james.mailbox.elasticsearch.MailboxIndexCreationUtil;
import org.elasticsearch.client.RestHighLevelClient;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;

public class TestDockerElasticSearchModule extends AbstractModule {

    private static class ESContainerCleanUp implements CleanupTasksPerformer.CleanupTask {

        private final DockerElasticSearch elasticSearch;

        private ESContainerCleanUp(DockerElasticSearch elasticSearch) {
            this.elasticSearch = elasticSearch;
        }

        @Override
        public Result run() {
            elasticSearch.cleanUpData();

            return Result.COMPLETED;
        }
    }

    private final DockerElasticSearch elasticSearch;

    public TestDockerElasticSearchModule(DockerElasticSearch elasticSearch) {
        this.elasticSearch = elasticSearch;
    }

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), CleanupTasksPerformer.CleanupTask.class)
            .addBinding()
            .toInstance(new ESContainerCleanUp(elasticSearch));
    }

    @Provides
    @Singleton
    protected RestHighLevelClient provideClientProvider() throws IOException {
        RestHighLevelClient client = elasticSearch.clientProvider().get();
        return MailboxIndexCreationUtil.prepareDefaultClient(client, ElasticSearchConfiguration.builder()
            .addHost(elasticSearch.getHttpHost())
            .build());
    }
}
