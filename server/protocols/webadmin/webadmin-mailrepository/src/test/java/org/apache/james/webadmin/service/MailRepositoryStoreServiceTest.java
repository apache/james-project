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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.memory.MemoryMailRepository;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.streams.Limit;
import org.apache.james.util.streams.Offset;
import org.apache.james.webadmin.dto.MailKeyDTO;
import org.apache.james.webadmin.dto.SingleMailRepositoryResponse;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MailRepositoryStoreServiceTest {
    private static final MailRepositoryPath FIRST_REPOSITORY_PATH = MailRepositoryPath.from("repository");
    private static final MailRepositoryPath SECOND_REPOSITORY_PATH = MailRepositoryPath.from("repository2");
    private static final MailKey NAME_1 = new MailKey("name1");
    private static final MailKey NAME_2 = new MailKey("name2");

    private MailRepositoryStore mailRepositoryStore;
    private MailRepositoryStoreService testee;
    private MemoryMailRepository repository;

    @BeforeEach
    void setUp() {
        mailRepositoryStore = mock(MailRepositoryStore.class);
        repository = new MemoryMailRepository();
        testee = new MailRepositoryStoreService(mailRepositoryStore);
    }

    @Test
    void listMailRepositoriesShouldReturnEmptyWhenEmpty() {
        when(mailRepositoryStore.getPaths()).thenReturn(Stream.empty());

        assertThat(testee.listMailRepositories()).isEmpty();
    }

    @Test
    void listMailRepositoriesShouldReturnOneRepositoryWhenOne() {
        when(mailRepositoryStore.getPaths())
            .thenReturn(Stream.of(FIRST_REPOSITORY_PATH));
        assertThat(testee.listMailRepositories())
            .extracting(SingleMailRepositoryResponse::getRepository)
            .containsOnly(FIRST_REPOSITORY_PATH.asString());
    }

    @Test
    void listMailRepositoriesShouldReturnTwoRepositoriesWhentwo() {
        when(mailRepositoryStore.getPaths())
            .thenReturn(Stream.of(FIRST_REPOSITORY_PATH, SECOND_REPOSITORY_PATH));
        assertThat(testee.listMailRepositories())
            .extracting(SingleMailRepositoryResponse::getRepository)
            .containsOnly(FIRST_REPOSITORY_PATH.asString(), SECOND_REPOSITORY_PATH.asString());
    }

    @Test
    void listMailsShouldThrowWhenMailRepositoryStoreThrows() throws Exception {
        when(mailRepositoryStore.getByPath(FIRST_REPOSITORY_PATH))
            .thenThrow(new MailRepositoryStore.MailRepositoryStoreException("message"));

        assertThatThrownBy(() -> testee.listMails(FIRST_REPOSITORY_PATH, Offset.none(), Limit.unlimited()))
            .isInstanceOf(MailRepositoryStore.MailRepositoryStoreException.class);
    }

    @Test
    void listMailsShouldReturnEmptyWhenMailRepositoryIsEmpty() throws Exception {
        when(mailRepositoryStore.getByPath(FIRST_REPOSITORY_PATH)).thenReturn(Stream.of(repository));

        assertThat(testee.listMails(FIRST_REPOSITORY_PATH, Offset.none(), Limit.unlimited()).get())
            .isEmpty();
    }

    @Test
    void listMailsShouldReturnContainedMailKeys() throws Exception {
        when(mailRepositoryStore.getByPath(FIRST_REPOSITORY_PATH)).thenReturn(Stream.of(repository));

        repository.store(FakeMail.builder()
            .name(NAME_1.asString())
            .build());
        repository.store(FakeMail.builder()
            .name(NAME_2.asString())
            .build());

        assertThat(testee.listMails(FIRST_REPOSITORY_PATH, Offset.none(), Limit.unlimited()).get())
            .containsOnly(new MailKeyDTO(NAME_1), new MailKeyDTO(NAME_2));
    }

    @Test
    void listMailsShouldApplyLimitAndOffset() throws Exception {
        when(mailRepositoryStore.getByPath(FIRST_REPOSITORY_PATH)).thenReturn(Stream.of(repository));

        repository.store(FakeMail.builder()
            .name(NAME_1.asString())
            .build());
        repository.store(FakeMail.builder()
            .name(NAME_2.asString())
            .build());
        repository.store(FakeMail.builder()
            .name("name3")
            .build());

        assertThat(testee.listMails(FIRST_REPOSITORY_PATH, Offset.from(1), Limit.from(1)).get())
            .containsOnly(new MailKeyDTO(NAME_2));
    }

    @Test
    void retrieveMessageShouldThrownWhenUnknownRepository() throws Exception {
        when(mailRepositoryStore.getByPath(FIRST_REPOSITORY_PATH)).thenReturn(Stream.of());

        assertThatThrownBy(() -> testee.retrieveMessage(FIRST_REPOSITORY_PATH, NAME_1))
            .isNotInstanceOf(NullPointerException.class);
    }

    @Test
    void retrieveMessageShouldThrowWhenMailRepositoryStoreThrows() throws Exception {
        when(mailRepositoryStore.getByPath(FIRST_REPOSITORY_PATH))
            .thenThrow(new MailRepositoryStore.MailRepositoryStoreException("message"));

        assertThatThrownBy(() -> testee.retrieveMessage(FIRST_REPOSITORY_PATH, NAME_1))
            .isInstanceOf(MailRepositoryStore.MailRepositoryStoreException.class);
    }

    @Test
    void retrieveMessageShouldReturnEmptyWhenMailNotFound() throws Exception {
        when(mailRepositoryStore.getByPath(FIRST_REPOSITORY_PATH)).thenReturn(Stream.of(repository));

        assertThat(testee.retrieveMessage(FIRST_REPOSITORY_PATH, NAME_1))
            .isEmpty();
    }

    @Test
    void retrieveMessageShouldReturnTheMessageWhenMailExists() throws Exception {
        when(mailRepositoryStore.getByPath(FIRST_REPOSITORY_PATH)).thenReturn(Stream.of(repository));

        FakeMail mail = FakeMail.builder()
            .name(NAME_1.asString())
            .fileName("mail.eml")
            .build();
        repository.store(mail);

        Optional<MimeMessage> mimeMessage = testee.retrieveMessage(FIRST_REPOSITORY_PATH, NAME_1);
        assertThat(mimeMessage).isNotEmpty();

        String eml = IOUtils.toString(new MimeMessageInputStream(mimeMessage.get()), StandardCharsets.UTF_8);
        String expectedContent = ClassLoaderUtils.getSystemResourceAsString("mail.eml");
        assertThat(eml).isEqualToNormalizingNewlines(expectedContent);
    }
}
