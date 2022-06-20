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

package org.apache.james.quota.search.opensearch;

import static org.apache.james.backends.es.v8.IndexCreationFactory.DOUBLE;
import static org.apache.james.backends.es.v8.IndexCreationFactory.KEYWORD;
import static org.apache.james.backends.es.v8.IndexCreationFactory.PROPERTIES;
import static org.apache.james.backends.es.v8.IndexCreationFactory.REQUIRED;
import static org.apache.james.backends.es.v8.IndexCreationFactory.ROUTING;
import static org.apache.james.backends.es.v8.IndexCreationFactory.TYPE;
import static org.apache.james.quota.search.elasticsearch.v8.json.JsonMessageConstants.DOMAIN;
import static org.apache.james.quota.search.elasticsearch.v8.json.JsonMessageConstants.QUOTA_RATIO;
import static org.apache.james.quota.search.elasticsearch.v8.json.JsonMessageConstants.USER;

import java.io.StringReader;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;

class QuotaRatioMappingFactory {

    public static TypeMapping getMappingContent() {
        return new TypeMapping.Builder()
            .withJson(new StringReader(generateMappingContent()))
            .build();
    }

    private static String generateMappingContent() {
        return "{" +
            "  \"" + ROUTING + "\": {" +
            "    \"" + REQUIRED + "\": true" +
            "  }," +
            "  \"" + PROPERTIES + "\": {" +
            "    \"" + USER + "\": {" +
            "      \"" + TYPE + "\": \"" + KEYWORD + "\"" +
            "    }," +
            "    \"" + DOMAIN + "\": {" +
            "      \"" + TYPE + "\": \"" + KEYWORD + "\"" +
            "    }," +
            "    \"" + QUOTA_RATIO + "\": {" +
            "      \"" + TYPE + "\": \"" + DOUBLE + "\"" +
            "    }" +
            "  }" +
            "}";
    }
}