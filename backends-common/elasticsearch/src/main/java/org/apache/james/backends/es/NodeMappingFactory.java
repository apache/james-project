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

import org.apache.james.util.streams.Iterators;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;

public class NodeMappingFactory {

    public static final String BOOLEAN = "boolean";
    public static final String TYPE = "type";
    public static final String LONG = "long";
    public static final String DOUBLE = "double";
    public static final String INDEX = "index";
    public static final String NOT_ANALYZED = "not_analyzed";
    public static final String STRING = "string";
    public static final String PROPERTIES = "properties";
    public static final String DATE = "date";
    public static final String FORMAT = "format";
    public static final String NESTED = "nested";
    public static final String FIELDS = "fields";
    public static final String RAW = "raw";
    public static final String SPLIT_EMAIL = "splitEmail";
    public static final String ANALYZER = "analyzer";
    public static final String SEARCH_ANALYZER = "search_analyzer";
    public static final String SNOWBALL = "snowball";
    public static final String IGNORE_ABOVE = "ignore_above";

    public static Client applyMapping(Client client, IndexName indexName, TypeName typeName, XContentBuilder mappingsSources) {
        if (!mappingAlreadyExist(client, indexName, typeName)) {
            createMapping(client, indexName, typeName, mappingsSources);
        }
        return client;
    }

    public static boolean mappingAlreadyExist(Client client, IndexName indexName, TypeName typeName) {
        return Iterators.toStream(client.admin()
            .indices()
            .prepareGetMappings(indexName.getValue())
            .execute()
            .actionGet()
            .getMappings()
            .valuesIt())
            .anyMatch(mapping -> mapping.keys().contains(typeName.getValue()));
    }

    public static void createMapping(Client client, IndexName indexName, TypeName typeName, XContentBuilder mappingsSources) {
        client.admin()
            .indices()
            .preparePutMapping(indexName.getValue())
            .setType(typeName.getValue())
            .setSource(mappingsSources)
            .execute()
            .actionGet();
    }

}
