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

package org.apache.james.vault.dto;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.util.ClassLoaderUtils.getSystemResourceAsString;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_WITH_SUBJECT;
import static org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTO.DeletedMessageDTO;
import static org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTO.StorageInformationDTO;
import static org.apache.james.vault.metadata.DeletedMessageVaultMetadataFixture.STORAGE_INFORMATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.internet.AddressException;

import org.apache.james.blob.api.HashBlobId;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.vault.metadata.DeletedMessageWithStorageInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

class DeletedMessageWithStorageInformationDTOTest {
    private static final StorageInformationDTO STORAGE_INFORMATION_DTO = StorageInformationDTO.toDTO(STORAGE_INFORMATION);

    private static final DeletedMessageDTO DELETED_MESSAGE_DTO = DeletedMessageDTO.toDTO(DELETED_MESSAGE);

    private static final DeletedMessageDTO DELETED_MESSAGE_WITH_SUBJECT_DTO = DeletedMessageDTO.toDTO(DELETED_MESSAGE_WITH_SUBJECT);

    private static final DeletedMessageWithStorageInformation DELETED_MESSAGE_WITH_STORAGE_INFO =
        new DeletedMessageWithStorageInformation(DELETED_MESSAGE_WITH_SUBJECT, STORAGE_INFORMATION);

    private static final DeletedMessageWithStorageInformationDTO DELETED_MESSAGE_WITH_STORAGE_INFO_DTO =
        DeletedMessageWithStorageInformationDTO.toDTO(DELETED_MESSAGE_WITH_STORAGE_INFO);

    private static final String STORAGE_INFORMATION_JSON = getSystemResourceAsString("json/storage_information.json");

    private static final String DELETED_MESSAGE_JSON = getSystemResourceAsString("json/deleted_message.json");
    private static final String DELETED_MESSAGE_WITH_SUBJECT_JSON = getSystemResourceAsString("json/deleted_message_with_subject.json");

    private static final String DELETED_MESSAGE_WITH_STORAGE_INFO_JSON =
        getSystemResourceAsString("json/deleted_message_with_storage_information.json");

    private ObjectMapper objectMapper;
    private DeletedMessageWithStorageInformationConverter converter;

    @BeforeEach
    void setup() {
        this.objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT);

        this.converter = new DeletedMessageWithStorageInformationConverter(
            new HashBlobId.Factory(),
            new InMemoryMessageId.Factory(),
            new InMemoryId.Factory());
    }

    @Test
    void shouldSerializeStorageInformation() throws Exception {
        assertThatJson(objectMapper.writeValueAsString(STORAGE_INFORMATION_DTO))
            .isEqualTo(STORAGE_INFORMATION_JSON);
    }

    @Test
    void shouldDeserializeStorageInformation() throws Exception {
        assertThat(converter.toDomainObject(objectMapper.readValue(STORAGE_INFORMATION_JSON, StorageInformationDTO.class)))
            .isEqualTo(STORAGE_INFORMATION);
    }

    @Test
    void shouldSerializeDeletedMessage() throws Exception {
        assertThatJson(objectMapper.writeValueAsString(DELETED_MESSAGE_DTO))
            .isEqualTo(DELETED_MESSAGE_JSON);
    }

    @Test
    void shouldDeserializeDeletedMessage() throws Exception {
        assertThat(converter.toDomainObject(objectMapper.readValue(DELETED_MESSAGE_JSON, DeletedMessageDTO.class)))
            .isEqualTo(DELETED_MESSAGE);
    }

    @Test
    void shouldSerializeDeletedMessageWithSubject() throws Exception {
        assertThatJson(objectMapper.writeValueAsString(DELETED_MESSAGE_WITH_SUBJECT_DTO))
            .isEqualTo(DELETED_MESSAGE_WITH_SUBJECT_JSON);
    }

    @Test
    void shouldDeserializeDeletedMessageWithSubject() throws Exception {
        assertThat(converter.toDomainObject(objectMapper.readValue(DELETED_MESSAGE_WITH_SUBJECT_JSON, DeletedMessageDTO.class)))
            .isEqualTo(DELETED_MESSAGE_WITH_SUBJECT);
    }

    @Test
    void shouldSerializeDeletedMessageWithStorageInformation() throws Exception {
        assertThatJson(objectMapper.writeValueAsString(DELETED_MESSAGE_WITH_STORAGE_INFO_DTO))
            .isEqualTo(DELETED_MESSAGE_WITH_STORAGE_INFO_JSON);
    }

    @Test
    void shouldDeserializeDeletedMessageWithStorageInformation() throws Exception {
        assertThat(converter.toDomainObject(objectMapper.readValue(DELETED_MESSAGE_WITH_STORAGE_INFO_JSON, DeletedMessageWithStorageInformationDTO.class)))
            .isEqualTo(DELETED_MESSAGE_WITH_STORAGE_INFO);
    }

    @Test
    void deserializingInvalidAddressesShouldThrow() {
        assertThatThrownBy(() -> converter.toDomainObject(
            objectMapper.readValue(getSystemResourceAsString("json/deleted_message_with_storage_information_invalid.json"),
                DeletedMessageWithStorageInformationDTO.class)))
            .isInstanceOf(AddressException.class);
    }
}
