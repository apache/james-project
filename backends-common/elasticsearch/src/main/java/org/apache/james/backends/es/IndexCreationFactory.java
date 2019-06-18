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

import static org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;

import javax.inject.Inject;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class IndexCreationFactory {

    public static class AliasSpecificationStep {
        private final int nbShards;
        private final int nbReplica;
        private final IndexName indexName;
        private final ImmutableList.Builder<AliasName> aliases;

        AliasSpecificationStep(int nbShards, int nbReplica, IndexName indexName) {
            this.nbShards = nbShards;
            this.nbReplica = nbReplica;
            this.indexName = indexName;
            this.aliases = ImmutableList.builder();
        }

        public AliasSpecificationStep addAlias(AliasName aliasName) {
            Preconditions.checkNotNull(aliasName);
            this.aliases.add(aliasName);
            return this;
        }

        public RestHighLevelClient createIndexAndAliases(RestHighLevelClient client) {
            return new IndexCreationPerformer(nbShards, nbReplica, indexName, aliases.build()).createIndexAndAliases(client);
        }
    }

    static class IndexCreationPerformer {
        private final int nbShards;
        private final int nbReplica;
        private final IndexName indexName;
        private final ImmutableList<AliasName> aliases;

        public IndexCreationPerformer(int nbShards, int nbReplica, IndexName indexName, ImmutableList<AliasName> aliases) {
            this.nbShards = nbShards;
            this.nbReplica = nbReplica;
            this.indexName = indexName;
            this.aliases = aliases;
        }

        public RestHighLevelClient createIndexAndAliases(RestHighLevelClient client) {
            Preconditions.checkNotNull(indexName);
            try {
                createIndexIfNeeded(client, indexName, generateSetting(nbShards, nbReplica));
                aliases.forEach(Throwing.<AliasName>consumer(alias -> createAliasIfNeeded(client, indexName, alias))
                    .sneakyThrow());
            } catch (IOException e) {
                LOGGER.error("Error while creating index : ", e);
            }
            return client;
        }

        private void createAliasIfNeeded(RestHighLevelClient client, IndexName indexName, AliasName aliasName) throws IOException {
            if (!aliasExist(client, aliasName)) {
                client.indices()
                    .updateAliases(
                        new IndicesAliasesRequest().addAliasAction(
                            new AliasActions(AliasActions.Type.ADD)
                                .index(indexName.getValue())
                                .alias(aliasName.getValue())));
            }
        }

        private boolean aliasExist(RestHighLevelClient client, AliasName aliasName) throws IOException {
            return client.indices()
                .existsAlias(new GetAliasesRequest().aliases(aliasName.getValue()));
        }

        private void createIndexIfNeeded(RestHighLevelClient client, IndexName indexName, XContentBuilder settings) throws IOException {
            try {
                client.indices()
                    .create(
                        new CreateIndexRequest(indexName.getValue())
                            .source(settings));
            } catch (ElasticsearchStatusException exception) {
                if (exception.getMessage().contains(INDEX_ALREADY_EXISTS_EXCEPTION_MESSAGE)) {
                    LOGGER.info("Index [{}] already exist", indexName);
                } else {
                    throw exception;
                }
            }
        }

        private XContentBuilder generateSetting(int nbShards, int nbReplica) throws IOException {
            return jsonBuilder()
                .startObject()
                    .startObject("settings")
                        .field("number_of_shards", nbShards)
                        .field("number_of_replicas", nbReplica)
                        .startObject("analysis")
                            .startObject("normalizer")
                                .startObject(CASE_INSENSITIVE)
                                    .field("type", "custom")
                                    .startArray("char_filter")
                                    .endArray()
                                    .startArray("filter")
                                        .value("lowercase")
                                        .value("asciifolding")
                                    .endArray()
                                .endObject()
                            .endObject()
                            .startObject("analyzer")
                                .startObject(KEEP_MAIL_AND_URL)
                                    .field("tokenizer", "uax_url_email")
                                    .startArray("filter")
                                        .value("lowercase")
                                        .value("stop")
                                    .endArray()
                                .endObject()
                                .startObject(SNOWBALL_KEEP_MAIL_AND_URL)
                                    .field("tokenizer", "uax_url_email")
                                    .startArray("filter")
                                        .value("lowercase")
                                        .value("stop")
                                        .value(ENGLISH_SNOWBALL)
                                    .endArray()
                                .endObject()
                            .endObject()
                            .startObject("filter")
                                .startObject(ENGLISH_SNOWBALL)
                                    .field("type", "snowball")
                                    .field("language", "English")
                                .endObject()
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexCreationFactory.class);
    private static final String INDEX_ALREADY_EXISTS_EXCEPTION_MESSAGE = "type=resource_already_exists_exception";

    private final int nbShards;
    private final int nbReplica;

    public static final String CASE_INSENSITIVE = "case_insensitive";
    public static final String KEEP_MAIL_AND_URL = "keep_mail_and_url";
    public static final String SNOWBALL_KEEP_MAIL_AND_URL = "snowball_keep_mail_and_token";
    public static final String ENGLISH_SNOWBALL = "english_snowball";

    @Inject
    public IndexCreationFactory(ElasticSearchConfiguration configuration) {
        this.nbShards = configuration.getNbShards();
        this.nbReplica = configuration.getNbReplica();
    }

    public AliasSpecificationStep useIndex(IndexName indexName) {
        Preconditions.checkNotNull(indexName);
        return new AliasSpecificationStep(nbShards, nbReplica, indexName);
    }
}
