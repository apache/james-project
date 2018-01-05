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

package org.apache.james.webadmin.utils;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

public class JsonExtractor<Request> {

    private final ObjectMapper objectMapper;
    private final Class<Request> type;

    public JsonExtractor(Class<Request> type) {
        this.objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module());
        this.type = type;
    }

    public Request parse(String text) throws JsonExtractException {
        try {
            return objectMapper.readValue(text, type);
        } catch (IOException | IllegalArgumentException e) {
            throw new JsonExtractException(e);
        }
    }

}
