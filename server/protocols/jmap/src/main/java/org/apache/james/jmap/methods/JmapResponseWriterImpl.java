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

package org.apache.james.jmap.methods;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.jmap.model.ProtocolResponse;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

public class JmapResponseWriterImpl implements JmapResponseWriter {

    private final Set<Module> jacksonModules;

    @Inject
    public JmapResponseWriterImpl(Set<Module> jacksonModules) {
        this.jacksonModules = jacksonModules;
    }

    @Override
    public Stream<ProtocolResponse> formatMethodResponse(Stream<JmapResponse> jmapResponses) {
        return jmapResponses.map(jmapResponse -> {
            ObjectMapper objectMapper = newConfiguredObjectMapper(jmapResponse);

            return new ProtocolResponse(
                    jmapResponse.getResponseName(),
                    objectMapper.valueToTree(jmapResponse.getResponse()),
                    jmapResponse.getClientId());
        });
    }
    
    private FilterProvider buildPropertiesFilter(Optional<Set<String>> properties) {
        PropertyFilter filter = properties
                .map(SimpleBeanPropertyFilter::filterOutAllExcept)
                .orElse(SimpleBeanPropertyFilter.serializeAll());
        return new SimpleFilterProvider().addFilter("propertiesFilter", filter);
    }
    
    private ObjectMapper newConfiguredObjectMapper(JmapResponse jmapResponse) {
        ObjectMapper objectMapper = new ObjectMapper().registerModules(jacksonModules)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        objectMapper.setFilterProvider(buildPropertiesFilter(jmapResponse.getProperties()));

        return objectMapper;
    }
}
