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

package org.apache.james.quota.search.elasticsearch.v7;

import static org.apache.james.backends.es.v7.IndexCreationFactory.DOUBLE;
import static org.apache.james.backends.es.v7.IndexCreationFactory.KEYWORD;
import static org.apache.james.backends.es.v7.IndexCreationFactory.PROPERTIES;
import static org.apache.james.backends.es.v7.IndexCreationFactory.REQUIRED;
import static org.apache.james.backends.es.v7.IndexCreationFactory.ROUTING;
import static org.apache.james.backends.es.v7.IndexCreationFactory.TYPE;
import static org.apache.james.quota.search.elasticsearch.v7.json.JsonMessageConstants.DOMAIN;
import static org.apache.james.quota.search.elasticsearch.v7.json.JsonMessageConstants.QUOTA_RATIO;
import static org.apache.james.quota.search.elasticsearch.v7.json.JsonMessageConstants.USER;
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