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

import static org.apache.james.quota.search.opensearch.json.JsonMessageConstants.DOMAIN;
import static org.apache.james.quota.search.opensearch.json.JsonMessageConstants.QUOTA_RATIO;
import static org.apache.james.quota.search.opensearch.json.JsonMessageConstants.USER;

import java.util.Map;

import org.opensearch.client.opensearch._types.mapping.DoubleNumberProperty;
import org.opensearch.client.opensearch._types.mapping.KeywordProperty;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.RoutingField;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;

import com.google.common.collect.ImmutableMap;

class QuotaRatioMappingFactory {

    public static TypeMapping getMappingContent() {
        return new TypeMapping.Builder()
            .routing(new RoutingField.Builder()
                .required(true)
                .build())
            .properties(generateProperties())
            .build();
    }

    private static Map<String, Property> generateProperties() {
        return new ImmutableMap.Builder<String, Property>()
            .put(USER, new Property.Builder()
                .keyword(new KeywordProperty.Builder().build())
                .build())
            .put(DOMAIN, new Property.Builder()
                .keyword(new KeywordProperty.Builder().build())
                .build())
            .put(QUOTA_RATIO, new Property.Builder()
                .double_(new DoubleNumberProperty.Builder().build())
                .build())
            .build();
    }
}