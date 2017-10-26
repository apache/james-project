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

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class IndexCreationFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexCreationFactory.class);
    private static final int DEFAULT_NB_SHARDS = 1;
    private static final int DEFAULT_NB_REPLICA = 0;
    public static final String CASE_INSENSITIVE = "case_insensitive";

    private IndexName indexName;
    private ArrayList<AliasName> aliases;
    private Optional<Integer> nbShards;
    private Optional<Integer> nbReplica;

    public IndexCreationFactory() {
        indexName = null;
        aliases = new ArrayList<>();
        nbShards = Optional.empty();
        nbReplica = Optional.empty();
    }

    public IndexCreationFactory useIndex(IndexName indexName) {
        Preconditions.checkNotNull(indexName);
        this.indexName = indexName;
        return this;
    }

    public IndexCreationFactory addAlias(AliasName aliasName) {
        Preconditions.checkNotNull(aliasName);
        this.aliases.add(aliasName);
        return this;
    }

    public IndexCreationFactory nbShards(int nbShards) {
        Preconditions.checkArgument(nbShards > 0, "You need the number of shards to be strictly positive");
        this.nbShards = Optional.of(nbShards);
        return this;
    }

    public IndexCreationFactory nbReplica(int nbReplica) {
        Preconditions.checkArgument(nbReplica >= 0, "You need the number of replica to be positive");
        this.nbReplica = Optional.of(nbReplica);
        return this;
    }

    public Client createIndexAndAliases(Client client) {
        Preconditions.checkNotNull(indexName);
        try {
            createIndexIfNeeded(client, indexName, generateSetting(
                nbShards.orElse(DEFAULT_NB_SHARDS),
                nbReplica.orElse(DEFAULT_NB_REPLICA)));
            aliases.forEach(alias -> createAliasIfNeeded(client, indexName, alias));
        } catch (IOException e) {
            LOGGER.error("Error while creating index : ", e);
        }
        return client;
    }

    private void createAliasIfNeeded(Client client, IndexName indexName, AliasName aliasName) {
        if (!aliasExist(client, aliasName)) {
            client.admin()
                .indices()
                .aliases( new IndicesAliasesRequest()
                    .addAlias(aliasName.getValue(), indexName.getValue()))
                .actionGet();
        }
    }

    private boolean aliasExist(Client client, AliasName aliasName) {
        return client.admin()
            .indices()
            .aliasesExist(new GetAliasesRequest()
                .aliases(aliasName.getValue()))
            .actionGet()
            .exists();
    }

    private void createIndexIfNeeded(Client client, IndexName indexName, XContentBuilder settings) {
        try {
            client.admin()
                .indices()
                .prepareCreate(indexName.getValue())
                .setSettings(settings)
                .execute()
                .actionGet();
        } catch (IndexAlreadyExistsException exception) {
            LOGGER.info("Index [" + indexName + "] already exist");
        }
    }

    private XContentBuilder generateSetting(int nbShards, int nbReplica) throws IOException {
        return jsonBuilder()
            .startObject()
                .field("number_of_shards", nbShards)
                .field("number_of_replicas", nbReplica)
                .startObject("analysis")
                    .startObject("analyzer")
                        .startObject(CASE_INSENSITIVE)
                            .field("tokenizer", "keyword")
                            .startArray("filter")
                                .value("lowercase")
                            .endArray()
                        .endObject()
                    .endObject()
                .endObject()
            .endObject();
    }

}
