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

import jakarta.mail.MessagingException;

import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class SplitMailTest {
    private static final Username USER_1 = Username.of("user1@gov.org");
    private static final Username USER_2 = Username.of("user2@gov.org");
    private static final Username USER_3 = Username.of("user3@gov.org");
    private static final Username USER_4 = Username.of("user4@gov.org");
    private static final Username USER_5 = Username.of("user5@gov.org");

    private FakeMailContext mailetContext;
    private SplitMail splitMail;

    @BeforeEach
    void setUp() throws Exception {
        mailetContext = FakeMailContext.defaultContext();
        splitMail = new SplitMail();
        splitMail.init(FakeMailetConfig.builder()
            .mailetContext(mailetContext)
            .setProperty("batchSize", "2")
            .build());
    }

    @Test
    void firstUsersBatchShouldBeSentDirectlyWhenExactlyBatchSize() throws Exception {
        Mail originalMail = createMail();
        originalMail.setRecipients(ImmutableList.of(USER_1.asMailAddress(),
            USER_2.asMailAddress()));

        splitMail.service(originalMail);

        assertThat(originalMail.getRecipients())
            .containsExactlyInAnyOrder(USER_1.asMailAddress(), USER_2.asMailAddress());
    }

    @Test
    void firstUsersBatchShouldBeSentDirectly() throws Exception {
        Mail originalMail = createMail();
        originalMail.setRecipients(ImmutableList.of(USER_1.asMailAddress(),
            USER_2.asMailAddress(),
            USER_3.asMailAddress(),
            USER_4.asMailAddress()));

        splitMail.service(originalMail);

        assertThat(originalMail.getRecipients())
            .containsExactlyInAnyOrder(USER_1.asMailAddress(), USER_2.asMailAddress());
    }

    @Test
    void remainingUsersBatchesShouldBeSentAsync() throws Exception {
        Mail originalMail = createMail();
        originalMail.setRecipients(ImmutableList.of(USER_1.asMailAddress(),
            USER_2.asMailAddress(),
            USER_3.asMailAddress(),
            USER_4.asMailAddress()));

        splitMail.service(originalMail);

        assertThat(mailetContext.getSentMails().getFirst().getRecipients())
            .containsExactly(USER_3.asMailAddress(), USER_4.asMailAddress());
    }

    @Test
    void remainingUsersBatchesShouldBeSentAsyncInSeveralBatches() throws Exception {
        Mail originalMail = createMail();
        originalMail.setRecipients(ImmutableList.of(USER_1.asMailAddress(),
            USER_2.asMailAddress(),
            USER_3.asMailAddress(),
            USER_4.asMailAddress(),
            USER_5.asMailAddress()));

        splitMail.service(originalMail);

        assertThat(mailetContext.getSentMails())
            .hasSize(2)
            .allMatch(mail -> mail.getRecipients().size() <= 2);
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
