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

package org.apache.james.mailbox.elasticsearch;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class IndexCreationFactory {

    private static Logger LOGGER = LoggerFactory.getLogger(IndexCreationFactory.class);

    public static ClientProvider createIndex(ClientProvider clientProvider, int nbShards, int nbReplica) {
        try {
            return createIndex(clientProvider, normalSettings(nbShards, nbReplica));
        } catch (IOException e) {
            LOGGER.error("Error while creating index : ", e);
            return clientProvider;
        }
    }

    public static ClientProvider createIndex(ClientProvider clientProvider) {
        try {
            return createIndex(clientProvider, settingForInMemory());
        } catch (IOException e) {
            LOGGER.error("Error while creating index : ", e);
            return clientProvider;
        }
    }

    private static ClientProvider createIndex(ClientProvider clientProvider, XContentBuilder settings) {
        try {
            try (Client client = clientProvider.get()) {
                client.admin()
                    .indices()
                    .prepareCreate(ElasticSearchIndexer.MAILBOX_INDEX)
                    .setSettings(settings)
                    .execute()
                    .actionGet();
            }
        } catch (IndexAlreadyExistsException exception) {
            LOGGER.info("Index [" + ElasticSearchIndexer.MAILBOX_INDEX + "] already exist");
        }
        return clientProvider;
    }

    public static XContentBuilder settingForInMemory() throws IOException {
        return generateSetting(1, 0, Optional.of(jsonBuilder().startObject().field("type", "memory").endObject()));
    }

    public static XContentBuilder normalSettings(int nbShards, int nbReplica) throws IOException{
        return generateSetting(nbShards, nbReplica, Optional.empty());
    }

    private static XContentBuilder generateSetting(int nbShards, int nbReplica, Optional<XContentBuilder> store) throws IOException {
        XContentBuilder contentBuilder = jsonBuilder().startObject()
            .field("number_of_shards", nbShards)
            .field("number_of_replicas", nbReplica);
        if (store.isPresent()) {
            contentBuilder.field("store", store.get());
        }
        return contentBuilder.endObject();
    }

}
