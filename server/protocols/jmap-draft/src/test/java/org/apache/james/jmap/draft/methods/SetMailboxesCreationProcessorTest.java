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

package org.apache.james.jmap.draft.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.jmap.draft.model.MailboxCreationId;
import org.apache.james.jmap.draft.model.MailboxFactory;
import org.apache.james.jmap.draft.model.SetError;
import org.apache.james.jmap.draft.model.SetMailboxesRequest;
import org.apache.james.jmap.draft.model.SetMailboxesResponse;
import org.apache.james.jmap.draft.model.mailbox.MailboxCreateRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryId.Factory;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.Before;
import org.junit.Test;

public class SetMailboxesCreationProcessorTest {

    private Factory mailboxIdFactory;
    private MailboxFactory mailboxFactory;
    private SetMailboxesCreationProcessor sut;
    private MailboxManager mailboxManager;

    @Before
    public void setup() {
        mailboxManager = mock(MailboxManager.class);
        mailboxIdFactory = new InMemoryId.Factory();
        sut = new SetMailboxesCreationProcessor(mailboxManager, mailboxFactory, mailboxIdFactory, new RecordingMetricFactory());
    }

    @Test
    public void processShouldReturnNotCreatedWhenMailboxExceptionOccured() throws Exception {
        MailboxCreationId parentId = MailboxCreationId.of("0");
        MailboxId parentMailboxId = mailboxIdFactory.fromString(parentId.getCreationId());
        MailboxCreationId mailboxCreationId = MailboxCreationId.of("1");
        SetMailboxesRequest request = SetMailboxesRequest.builder()
                .create(mailboxCreationId, MailboxCreateRequest.builder().name("name").parentId(parentId).build())
                .build();

        MailboxSession mailboxSession = mock(MailboxSession.class);
        when(mailboxManager.getMailbox(parentMailboxId, mailboxSession))
            .thenThrow(new MailboxException());

        SetMailboxesResponse setMailboxesResponse = sut.process(request, mailboxSession);
        assertThat(setMailboxesResponse.getCreated()).isEmpty();
        assertThat(setMailboxesResponse.getNotCreated()).containsEntry(mailboxCreationId, 
                SetError.builder()
                    .type(SetError.Type.ERROR)
                    .description("An error occurred when creating the mailbox '1'")
                    .build());
    }
}
