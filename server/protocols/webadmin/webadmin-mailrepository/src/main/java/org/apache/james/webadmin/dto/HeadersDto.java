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
import com.google.common.collect.ImmutableListMultimap;

public class HeadersDto {
    private ImmutableListMultimap<String, String> headers;

    public HeadersDto(ImmutableListMultimap<String, String> headers) {
        this.headers = headers;
    }

    @JsonValue
    public ImmutableListMultimap<String, String> getHeaders() {
        return headers;
    }
}
