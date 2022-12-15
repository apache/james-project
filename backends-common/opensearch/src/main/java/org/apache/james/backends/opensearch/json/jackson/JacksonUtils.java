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

package org.apache.james.backends.opensearch.json.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;

import jakarta.json.JsonException;
import jakarta.json.stream.JsonGenerationException;
import jakarta.json.stream.JsonParsingException;

class JacksonUtils {
    public static JsonException convertException(IOException ioe) {
        if (ioe instanceof com.fasterxml.jackson.core.JsonGenerationException) {
            return new JsonGenerationException(ioe.getMessage(), ioe);

        } else if (ioe instanceof JsonParseException) {
            JsonParseException jpe = (JsonParseException) ioe;
            return new JsonParsingException(ioe.getMessage(), jpe, new JacksonJsonpLocation(jpe.getLocation()));

        } else {
            return new JsonException("Jackson exception", ioe);
        }
    }
}
