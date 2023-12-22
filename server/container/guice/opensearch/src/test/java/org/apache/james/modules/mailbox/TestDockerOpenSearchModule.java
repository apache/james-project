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

import org.apache.james.CleanupTasksPerformer;
import org.apache.james.backends.opensearch.DockerOpenSearch;
import org.apache.james.backends.opensearch.OpenSearchConfiguration;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class TestDockerOpenSearchModule extends AbstractModule {

    private static class ESContainerCleanUp implements CleanupTasksPerformer.CleanupTask {

        private final DockerOpenSearch elasticSearch;

        private ESContainerCleanUp(DockerOpenSearch elasticSearch) {
            this.elasticSearch = elasticSearch;
        }

        @Override
        public Result run() {
            elasticSearch.cleanUpData();

            return Result.COMPLETED;
        }
    }

    private final DockerOpenSearch elasticSearch;

    public TestDockerOpenSearchModule(DockerOpenSearch elasticSearch) {
        this.elasticSearch = elasticSearch;
    }

    @Override
    protected void configure() {
        bind(OpenSearchConfiguration.class).toInstance(elasticSearch.configuration());
        Multibinder.newSetBinder(binder(), CleanupTasksPerformer.CleanupTask.class)
            .addBinding()
            .toInstance(new ESContainerCleanUp(elasticSearch));
    }

}
