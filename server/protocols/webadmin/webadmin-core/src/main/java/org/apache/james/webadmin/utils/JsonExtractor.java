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
import java.util.List;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class JsonExtractor<RequestT> {

    private final ObjectMapper objectMapper;
    private final Class<RequestT> type;

    public JsonExtractor(Class<RequestT> type, Module... modules) {
        this(type, ImmutableList.copyOf(modules));
    }

    public JsonExtractor(Class<RequestT> type, List<Module> modules) {
        this.objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new GuavaModule())
            .registerModules(modules);
        this.type = type;
    }

    public RequestT parse(String text) throws JsonExtractException {
        Preconditions.checkNotNull(text);
        try {
            return objectMapper.readValue(text, type);
        } catch (IOException | IllegalArgumentException e) {
            throw new JsonExtractException(e);
        }
    }

}
