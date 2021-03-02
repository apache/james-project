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

package org.apache.james.backends.es.v7;

import java.io.IOException;

import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;

public class NodeMappingFactory {

    public static final String BOOLEAN = "boolean";
    public static final String TYPE = "type";
    public static final String LONG = "long";
    public static final String DOUBLE = "double";
    public static final String INDEX = "index";
    public static final String NOT_ANALYZED = "not_analyzed";
    public static final String STRING = "string";
    public static final String TEXT = "text";
    public static final String KEYWORD = "keyword";
    public static final String PROPERTIES = "properties";
    public static final String ROUTING = "_routing";
    public static final String REQUIRED = "required";
    public static final String DATE = "date";
    public static final String FORMAT = "format";
    public static final String NESTED = "nested";
    public static final String FIELDS = "fields";
    public static final String RAW = "raw";
    public static final String ANALYZER = "analyzer";
    public static final String NORMALIZER = "normalizer";
    public static final String SEARCH_ANALYZER = "search_analyzer";
    public static final String SNOWBALL = "snowball";
    public static final String IGNORE_ABOVE = "ignore_above";

    public static ReactorElasticSearchClient applyMapping(ReactorElasticSearchClient client, IndexName indexName, XContentBuilder mappingsSources) throws IOException {
        createMapping(client, indexName, mappingsSources);

        /*if (!mappingAlreadyExist(client, indexName)) {
            createMapping(client, indexName, mappingsSources);
        }*/
        return client;
    }

    //TODO: since ElasticSearch version 7 this check does not work anymore. Find another way to check whether mapping exists or not
    // ElasticSearch 6.3.2 does not support field master_timeout that is set up by 6.4.3 REST client when relying on getMapping
    @SuppressWarnings("deprecation")
    /*public static boolean mappingAlreadyExist(ReactorElasticSearchClient client, IndexName indexName) throws IOException {
        try {
            client.getLowLevelClient().performRequest(new Request("GET", "/" + indexName.getValue() + "/_mapping/"));
            return true;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() != HttpStatus.SC_NOT_FOUND) {
                throw e;
            }
        }
        return false;
    }*/

    public static void createMapping(ReactorElasticSearchClient client, IndexName indexName, XContentBuilder mappingsSources) throws IOException {
        client.indices().putMapping(
            new PutMappingRequest(indexName.getValue())
                .source(mappingsSources),
            RequestOptions.DEFAULT);
    }

}
