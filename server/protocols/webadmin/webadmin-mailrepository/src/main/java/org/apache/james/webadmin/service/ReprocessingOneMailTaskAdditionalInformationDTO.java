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
package org.apache.james.webadmin.service;

import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReprocessingOneMailTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    static final AdditionalInformationDTOModule<ReprocessingOneMailTask.AdditionalInformation, ReprocessingOneMailTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(ReprocessingOneMailTask.AdditionalInformation.class)
            .convertToDTO(ReprocessingOneMailTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new ReprocessingOneMailTask.AdditionalInformation(
                MailRepositoryPath.from(dto.repositoryPath),
                dto.targetQueue,
                new MailKey(dto.mailKey),
                dto.targetProcessor
            ))
            .toDTOConverter((details, type) -> new ReprocessingOneMailTaskAdditionalInformationDTO(
                type,
                details.getRepositoryPath(),
                details.getTargetQueue(),
                details.getMailKey(),
                details.getTargetProcessor()))
            .typeName(ReprocessingOneMailTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String type;
    private final String repositoryPath;
    private final String targetQueue;
    private final String mailKey;
    private final Optional<String> targetProcessor;

    public ReprocessingOneMailTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                           @JsonProperty("repositoryPath") String repositoryPath,
                                                           @JsonProperty("targetQueue") String targetQueue,
                                                           @JsonProperty("mailKey") String mailKey,
                                                           @JsonProperty("targetProcessor") Optional<String> targetProcessor) {
        this.type = type;
        this.repositoryPath = repositoryPath;
        this.targetQueue = targetQueue;
        this.mailKey = mailKey;
        this.targetProcessor = targetProcessor;
    }

    @Override
    public String getType() {
        return type;
    }

    public String getRepositoryPath() {
        return repositoryPath;
    }

    public String getTargetQueue() {
        return targetQueue;
    }

    public String getMailKey() {
        return mailKey;
    }

    public Optional<String> getTargetProcessor() {
        return targetProcessor;
    }
}
