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

import static com.jayway.awaitility.Awaitility.await;
import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

import org.elasticsearch.action.admin.indices.flush.FlushAction;
import org.elasticsearch.action.admin.indices.flush.FlushRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import com.jayway.awaitility.Duration;

public class EmbeddedElasticSearch extends ExternalResource {
    private final Supplier<Path> folder;
    private Node node;

    private static Path createTempDir(TemporaryFolder temporaryFolder) {
        try {
            return temporaryFolder.newFolder().toPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public EmbeddedElasticSearch(TemporaryFolder temporaryFolder) {
        this(() -> EmbeddedElasticSearch.createTempDir(temporaryFolder));
    }

    public EmbeddedElasticSearch(Path folder) {
        this(() -> folder);
    }

    private EmbeddedElasticSearch(Supplier<Path> folder) {
        this.folder = folder;
    }

    @Override
    public void before() {
        node = nodeBuilder().local(true)
            .settings(Settings.builder()
                .put("path.home", folder.get().toAbsolutePath())
                .build())
            .node();
        node.start();
    }

    @Override
    public void after() {
        node.close();
    }

    public Node getNode() {
        return node;
    }

    /**
     * Sometimes, tests are too fast.
     * This method ensure that ElasticSearch service is up and indices are updated
     */
    public void awaitForElasticSearch() {
        await().atMost(Duration.TEN_SECONDS)
            .until(this::flush);
    }

    private boolean flush() {
        try (Client client = node.client()) {
            new FlushRequestBuilder(client, FlushAction.INSTANCE).setForce(true).get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
