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

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RepublishNotProcessedMailsTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<RepublishNotprocessedMailsTask.AdditionalInformation, RepublishNotProcessedMailsTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(RepublishNotprocessedMailsTask.AdditionalInformation.class)
            .convertToDTO(RepublishNotProcessedMailsTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new RepublishNotprocessedMailsTask.AdditionalInformation(
                MailQueueName.of(dto.mailQueue),
                dto.olderThan,
                dto.nbRequeuedMails,
                dto.timestamp))
            .toDTOConverter((details, type) -> new RepublishNotProcessedMailsTaskAdditionalInformationDTO(
                type,
                details.getMailQueue().asString(),
                details.getOlderThan(),
                details.getNbRequeuedMails(),
                details.timestamp()))
            .typeName(RepublishNotprocessedMailsTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String type;
    private final String mailQueue;

    private final long nbRequeuedMails;
    private final Instant olderThan;
    private final Instant timestamp;

    public RepublishNotProcessedMailsTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                  @JsonProperty("mailQueue") String mailQueue,
                                                                  @JsonProperty("olderThan") Instant olderThan,
                                                                  @JsonProperty("nbRequeuedMails") long nbRequeuedMails,
                                                                  @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.mailQueue = mailQueue;
        this.olderThan = olderThan;
        this.nbRequeuedMails = nbRequeuedMails;
        this.timestamp = timestamp;
    }

    public String getMailQueue() {
        return mailQueue;
    }

    public long getNbRequeuedMails() {
        return nbRequeuedMails;
    }

    public Instant getOlderThan() {
        return olderThan;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
}
