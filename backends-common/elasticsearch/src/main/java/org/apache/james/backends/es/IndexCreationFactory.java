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

import javax.inject.Inject;

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
    public static final String CASE_INSENSITIVE = "case_insensitive";
    public static final String KEEP_MAIL_AND_URL = "keep_mail_and_url";
    public static final String SNOWBALL_KEEP_MAIL_AND_URL = "snowball_keep_mail_and_token";
    public static final String ENGLISH_SNOWBALL = "english_snowball";

    private IndexName indexName;
    private ArrayList<AliasName> aliases;
    private int nbShards;
    private int nbReplica;

    @Inject
    public IndexCreationFactory(ElasticSearchConfiguration configuration) {
        indexName = null;
        aliases = new ArrayList<>();
        nbShards = configuration.getNbShards();
        nbReplica = configuration.getNbReplica();
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

    public Client createIndexAndAliases(Client client) {
        Preconditions.checkNotNull(indexName);
        try {
            createIndexIfNeeded(client, indexName, generateSetting(nbShards, nbReplica));
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
                .aliases(new IndicesAliasesRequest()
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
            LOGGER.info("Index [{}] already exist", indexName);
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
                    .startObject("analyzer")
                        .startObject(KEEP_MAIL_AND_URL)
                            .field("tokenizer", "uax_url_email")
                            .startArray("filter")
                                .value("lowercase")
                                .value("stop")
                            .endArray()
                        .endObject()
                    .endObject()
                    .startObject("filter")
                        .startObject(ENGLISH_SNOWBALL)
                            .field("type", "snowball")
                            .field("language", "English")
                        .endObject()
                    .endObject()
                    .startObject("analyzer")
                        .startObject(SNOWBALL_KEEP_MAIL_AND_URL)
                        .field("tokenizer", "uax_url_email")
                            .startArray("filter")
                                .value("lowercase")
                                .value("stop")
                                .value(ENGLISH_SNOWBALL)
                            .endArray()
                        .endObject()
                    .endObject()
                .endObject()
            .endObject();
    }

}
