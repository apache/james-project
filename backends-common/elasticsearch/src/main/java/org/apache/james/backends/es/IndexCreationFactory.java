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

import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexCreationFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexCreationFactory.class);
    private static final int DEFAULT_NB_SHARDS = 1;
    private static final int DEFAULT_NB_REPLICA = 0;
    public static final String CASE_INSENSITIVE = "case_insensitive";

    public static Client createIndex(Client client, IndexName name, int nbShards, int nbReplica) {
        try {
            return createIndex(client, name, generateSetting(nbShards, nbReplica));
        } catch (IOException e) {
            LOGGER.error("Error while creating index : ", e);
            return client;
        }
    }

    public static Client createIndex(Client client, IndexName name) {
        return createIndex(client, name, DEFAULT_NB_SHARDS, DEFAULT_NB_REPLICA);
    }

    private static Client createIndex(Client client, IndexName name, XContentBuilder settings) {
        try {
            client.admin()
                .indices()
                .prepareCreate(name.getValue())
                .setSettings(settings)
                .execute()
                .actionGet();
        } catch (IndexAlreadyExistsException exception) {
            LOGGER.info("Index [" + name + "] already exist");
        }
        return client;
    }

    private static XContentBuilder generateSetting(int nbShards, int nbReplica) throws IOException {
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
