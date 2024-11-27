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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.mail.MessagingException;

import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

class MailToAllUsersTest {
    private static final Username USER_1 = Username.of("user1@gov.org");
    private static final Username USER_2 = Username.of("user2@gov.org");
    private static final Username USER_3 = Username.of("user3@gov.org");
    private static final Username USER_4 = Username.of("user4@gov.org");

    private UsersRepository usersRepository;
    private MailToAllUsers testee;

    @BeforeEach
    void setUp() throws Exception {
        usersRepository = mock(UsersRepository.class);
        testee = new MailToAllUsers(usersRepository);
    }

    @Test
    void shouldSendAMailToAllUsers() throws Exception {
        when(usersRepository.listReactive())
            .thenReturn(Flux.just(USER_1, USER_2, USER_3, USER_4));

        Mail originalMail = createMail();
        testee.service(originalMail);

        assertThat(originalMail.getRecipients())
            .containsExactlyInAnyOrder(USER_1.asMailAddress(), USER_2.asMailAddress(),
                USER_3.asMailAddress(), USER_4.asMailAddress());
    }


    private Mail createMail() throws MessagingException {
        return FakeMail.builder()
            .name("name")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSender("admin@gov.org")
                .setSubject("Hi all")
                .addToRecipient("all@gov.org"))
            .recipient("all@gov.org")
            .build();
    }
}
