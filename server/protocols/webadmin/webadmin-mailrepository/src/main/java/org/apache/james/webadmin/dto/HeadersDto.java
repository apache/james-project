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

package org.apache.james.webadmin.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class HeadersDto {
    private Multimap<String, String> headers;

    public HeadersDto() {
        headers = ArrayListMultimap.create();
    }

    public void add(String key, String value) {
        headers.put(key, value);
    }

    @JsonValue
    public Multimap<String, String> getHeaders() {
        return headers;
    }
}
