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

import java.io.IOException;

import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.github.fge.lambdas.Throwing;

public class HeadersDtoJsonSerializer extends JsonSerializer<HeadersDto> {
    
    @SuppressWarnings("unchecked")
    @Override
    public void serialize(
      HeadersDto headers, JsonGenerator jgen, SerializerProvider provider) 
      throws IOException, JsonProcessingException {
        jgen.writeStartObject();
        headers.getHeaders().forEach(Throwing.biConsumer((key, values) -> {
            jgen.writeArrayFieldStart((String) key);
            ((List<String>) values)
                .forEach(Throwing.consumer((value) -> jgen.writeString((String) value)).sneakyThrow());
            jgen.writeEndArray();
        }).sneakyThrow());
        jgen.writeEndObject();
    }
}