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

package org.apache.james.quota.search.elasticsearch;

import static org.apache.james.backends.es.NodeMappingFactory.DOUBLE;
import static org.apache.james.backends.es.NodeMappingFactory.KEYWORD;
import static org.apache.james.backends.es.NodeMappingFactory.PROPERTIES;
import static org.apache.james.backends.es.NodeMappingFactory.REQUIRED;
import static org.apache.james.backends.es.NodeMappingFactory.ROUTING;
import static org.apache.james.backends.es.NodeMappingFactory.TYPE;
import static org.apache.james.quota.search.elasticsearch.json.JsonMessageConstants.DOMAIN;
import static org.apache.james.quota.search.elasticsearch.json.JsonMessageConstants.QUOTA_RATIO;
import static org.apache.james.quota.search.elasticsearch.json.JsonMessageConstants.USER;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;

import org.elasticsearch.common.xcontent.XContentBuilder;

class QuotaRatioMappingFactory {

    public static XContentBuilder getMappingContent() {
        try {
            return jsonBuilder()
                .startObject()
                    .startObject(ROUTING)
                        .field(REQUIRED, true)
                    .endObject()

                    .startObject(PROPERTIES)

                        .startObject(USER)
                            .field(TYPE, KEYWORD)
                        .endObject()

                        .startObject(DOMAIN)
                            .field(TYPE, KEYWORD)
                        .endObject()

                        .startObject(QUOTA_RATIO)
                            .field(TYPE, DOUBLE)
                        .endObject()
                    .endObject()
                .endObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}