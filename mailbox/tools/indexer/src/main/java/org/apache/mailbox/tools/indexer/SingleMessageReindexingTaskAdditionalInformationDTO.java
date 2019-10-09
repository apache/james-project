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
package org.apache.mailbox.tools.indexer;

import java.util.function.Function;

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SingleMessageReindexingTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    static final Function<MailboxId.Factory, AdditionalInformationDTOModule<SingleMessageReindexingTask.AdditionalInformation, SingleMessageReindexingTaskAdditionalInformationDTO>> SERIALIZATION_MODULE =
        factory ->
            DTOModule.forDomainObject(SingleMessageReindexingTask.AdditionalInformation.class)
                .convertToDTO(SingleMessageReindexingTaskAdditionalInformationDTO.class)
                .toDomainObjectConverter(dto -> new SingleMessageReindexingTask.AdditionalInformation(factory.fromString(dto.mailboxId), MessageUid.of(dto.getUid())))
                .toDTOConverter((details, type) -> new SingleMessageReindexingTaskAdditionalInformationDTO(type, details.getMailboxId(), details.getUid()))
                .typeName(SingleMessageReindexingTask.MESSAGE_RE_INDEXING.asString())
                .withFactory(AdditionalInformationDTOModule::new);

    private final String type;
    private final String mailboxId;
    private final long uid;

    private SingleMessageReindexingTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                @JsonProperty("mailboxId") String mailboxId,
                                                                @JsonProperty("uid") long uid) {
        this.type = type;
        this.mailboxId = mailboxId;
        this.uid = uid;
    }

    public String getMailboxId() {
        return mailboxId;
    }

    public long getUid() {
        return uid;
    }

    @Override
    public String getType() {
        return type;
    }

    public static SingleMessageReindexingTaskAdditionalInformationDTO of(SingleMessageReindexingTask task) {
        return new SingleMessageReindexingTaskAdditionalInformationDTO(task.type().asString(), task.getMailboxId().serialize(), task.getUid().asLong());
    }
}
