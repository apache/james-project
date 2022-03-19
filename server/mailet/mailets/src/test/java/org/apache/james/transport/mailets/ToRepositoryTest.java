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
package org.apache.james.transport.mailets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.MessagingException;

import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToRepositoryTest {
    public static final String REPOSITORY_PATH = "file://var/mail/any";

    private ToRepository mailet;
    private MailRepositoryStore mailRepositoryStore;
    private FakeMail message;


    @BeforeEach
    void setup() throws Exception {
        mailRepositoryStore = mock(MailRepositoryStore.class);
        mailet = new ToRepository(mailRepositoryStore);
        message = MailUtil.createMockMail2Recipients(MailUtil.createMimeMessage());
    }

    @Test
    void getMailetInfoShouldReturnExpectedContent() {
        String expected = "ToRepository Mailet";

        String actual = mailet.getMailetInfo();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void initShouldNotThrowWhenInvalidPassThrough() throws Exception {
        MailetConfig mockedMailetConfig = mock(MailetConfig.class);
        when(mockedMailetConfig.getInitParameter("passThrough")).thenThrow(new RuntimeException());
        when(mockedMailetConfig.getInitParameter("repositoryPath")).thenReturn(REPOSITORY_PATH);

        mailet.init(mockedMailetConfig);
    }

    @Test
    void initShouldThrowWhenMailStoreThrows() throws Exception {
        when(mailRepositoryStore.select(any())).thenThrow(new RuntimeException());
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("repositoryPath", REPOSITORY_PATH)
            .build();

        assertThatThrownBy(() -> mailet.init(mailetConfig))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void serviceShouldStoreMailIntoRepository() throws Exception {
        MailRepository mailRepository = mock(MailRepository.class);
        when(mailRepositoryStore.select(any())).thenReturn(mailRepository);

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("repositoryPath", REPOSITORY_PATH)
            .build();
        mailet.init(mailetConfig);

        mailet.service(message);

        verify(mailRepository).store(message);
    }

    @Test
    void serviceShouldGhostMailIfPassThroughNotSet() throws Exception {
        MailRepository mailRepository = mock(MailRepository.class);
        when(mailRepositoryStore.select(any())).thenReturn(mailRepository);

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("repositoryPath", REPOSITORY_PATH)
                .build();
        mailet.init(mailetConfig);

        mailet.service(message);

        assertThat(message.getState()).isEqualTo(Mail.GHOST);
    }

    @Test
    void serviceShouldGhostMailIfPassThroughSetToFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("passThrough", "false")
            .setProperty("repositoryPath", REPOSITORY_PATH)
            .build();
        MailRepository mailRepository = mock(MailRepository.class);
        when(mailRepositoryStore.select(any())).thenReturn(mailRepository);
        mailet.init(mailetConfig);

        mailet.service(message);

        assertThat(message.getState()).isEqualTo(Mail.GHOST);
    }

    @Test
    void serviceShouldNotGhostMailIfPassThroughSetToTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("passThrough", "true")
            .setProperty("repositoryPath", REPOSITORY_PATH)
            .build();
        MailRepository mailRepository = mock(MailRepository.class);
        when(mailRepositoryStore.select(any())).thenReturn(mailRepository);
        mailet.init(mailetConfig);

        mailet.service(message);

        assertThat(message.getState()).isNull();
    }

}
