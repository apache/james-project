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

package org.apache.james.transport.mailets.jsieve.delivery;

import static org.apache.james.transport.mailets.jsieve.delivery.SieveExecutor.SIEVE_NOTIFICATION;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.commons.logging.Log;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.server.core.MailImpl;
import org.apache.james.transport.mailets.jsieve.ResourceLocator;
import org.apache.mailet.base.test.FakeMailContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SieveExecutorTest {
    SieveExecutor testee;
    FakeMailContext mailetContext;

    @BeforeEach
    void setUp() throws Exception {
        mailetContext = FakeMailContext.defaultContext();
        testee = SieveExecutor.builder()
            .mailetContext(mailetContext)
            .sievePoster(mock(SievePoster.class))
            .resourceLocator(mock(ResourceLocator.class))
            .log(mock(Log.class))
            .build();
    }

    @Test
    void handleFailureShouldSendAMailWithSieveNotificationAttribute() throws Exception {
        testee.handleFailure(RECIPIENT1, MailImpl.builder()
            .name("mymail")
            .sender("sender@localhost")
            .addRecipient(RECIPIENT1)
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .setSubject("test")
                .setText("this is the content"))
            .build(), new Exception());

        assertThat(mailetContext.getSentMails())
            .hasSize(1)
            .allSatisfy(sentMail -> assertThat(sentMail.getAttributes()).containsKey(SIEVE_NOTIFICATION));
    }
}