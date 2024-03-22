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
package org.apache.james.server.task.json;

import java.io.IOException;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.task.TaskExecutionDetails;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableSet;

public class JsonTaskAdditionalInformationSerializer {

    public static JsonTaskAdditionalInformationSerializer of(AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO>... modules) {
        return new JsonTaskAdditionalInformationSerializer(ImmutableSet.copyOf(modules));
    }

    public static class InvalidAdditionalInformationException extends RuntimeException {
        public InvalidAdditionalInformationException(JsonGenericSerializer.InvalidTypeException original) {
            super(original);
        }
    }

    public static class UnknownAdditionalInformationException extends RuntimeException {
        public UnknownAdditionalInformationException(JsonGenericSerializer.UnknownTypeException original) {
            super(original);
        }
    }

    private JsonGenericSerializer<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> jsonGenericSerializer;

    @Inject
    private JsonTaskAdditionalInformationSerializer(@Named(TaskModuleInjectionKeys.ADDITIONAL_INFORMATION_DTO) Set<AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO>> modules) {
        jsonGenericSerializer = JsonGenericSerializer.forModules(modules).withoutNestedType();
    }

    public String serialize(TaskExecutionDetails.AdditionalInformation additionalInformation) throws JsonProcessingException {
        try {
            return jsonGenericSerializer.serialize(additionalInformation);
        } catch (JsonGenericSerializer.UnknownTypeException e) {
            throw new UnknownAdditionalInformationException(e);
        }
    }

    public TaskExecutionDetails.AdditionalInformation deserialize(String value) throws IOException {
        try {
            return jsonGenericSerializer.deserialize(value);
        } catch (JsonGenericSerializer.UnknownTypeException e) {
            throw new UnknownAdditionalInformationException(e);
        } catch (JsonGenericSerializer.InvalidTypeException e) {
            throw new InvalidAdditionalInformationException(e);
        }
    }

}
