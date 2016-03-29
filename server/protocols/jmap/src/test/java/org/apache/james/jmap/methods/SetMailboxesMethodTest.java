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

package org.apache.james.jmap.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.GetMailboxesRequest;
import org.apache.james.jmap.model.MailboxCreationId;
import org.apache.james.jmap.model.SetMailboxesRequest;
import org.apache.james.jmap.model.SetMailboxesResponse;
import org.apache.james.jmap.model.mailbox.Mailbox;
import org.apache.james.jmap.model.mailbox.MailboxRequest;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.store.TestId;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public class SetMailboxesMethodTest {

    private static final ImmutableSet<SetMailboxesProcessor<TestId>> NO_PROCESSOR = ImmutableSet.of();

    @Test
    public void requestHandledShouldBeSetMailboxes() {
        assertThat(new SetMailboxesMethod<>(NO_PROCESSOR).requestHandled().getName()).isEqualTo("setMailboxes");
    }

    @Test
    public void requestTypeShouldBeSetMailboxes() {
        assertThat(new SetMailboxesMethod<>(NO_PROCESSOR).requestType()).isEqualTo(SetMailboxesRequest.class);
    }

    @Test
    public void processShouldThrowWhenNullJmapRequest() {
        MailboxSession session = mock(MailboxSession.class);
        JmapRequest nullJmapRequest = null;
        assertThatThrownBy(() -> new SetMailboxesMethod<>(NO_PROCESSOR).process(nullJmapRequest, ClientId.of("clientId"), session))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenNullClientId() {
        MailboxSession session = mock(MailboxSession.class);
        JmapRequest jmapRequest = mock(JmapRequest.class);
        ClientId nullClientId = null;
        assertThatThrownBy(() -> new SetMailboxesMethod<>(NO_PROCESSOR).process(jmapRequest, nullClientId, session))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenNullMailboxSession() {
        MailboxSession nullMailboxSession = null;
        JmapRequest jmapRequest = mock(JmapRequest.class);
        assertThatThrownBy(() -> new SetMailboxesMethod<>(NO_PROCESSOR).process(jmapRequest, ClientId.of("clientId"), nullMailboxSession))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenJmapRequestTypeMismatch() {
        MailboxSession session = mock(MailboxSession.class);
        JmapRequest getMailboxesRequest = GetMailboxesRequest.builder().build();
        assertThatThrownBy(() -> new SetMailboxesMethod<>(NO_PROCESSOR).process(getMailboxesRequest, ClientId.of("clientId"), session))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void processShouldCallCreatorProcessorWhenCreationRequest() {
        MailboxCreationId creationId = MailboxCreationId.of("create-id01");
        MailboxRequest fooFolder = MailboxRequest.builder().name("fooFolder").build();
        SetMailboxesRequest creationRequest = SetMailboxesRequest.builder().create(creationId, fooFolder).build();

        Mailbox createdfooFolder = Mailbox.builder().name("fooFolder").id("fooId").build();
        SetMailboxesResponse creationResponse = SetMailboxesResponse.builder().creation(creationId, createdfooFolder).build();
        JmapResponse jmapResponse = JmapResponse.builder()
            .response(creationResponse)
            .clientId(ClientId.of("clientId"))
            .responseName(SetMailboxesMethod.RESPONSE_NAME)
            .build();

        MailboxSession session = mock(MailboxSession.class);
        @SuppressWarnings("unchecked")
        SetMailboxesProcessor<TestId> creatorProcessor = mock(SetMailboxesProcessor.class);
        when(creatorProcessor.process(creationRequest)).thenReturn(creationResponse);

        Stream<JmapResponse> actual =
            new SetMailboxesMethod<>(ImmutableSet.of(creatorProcessor))
                    .process(creationRequest, ClientId.of("clientId"), session);

        assertThat(actual).contains(jmapResponse);
    }

}
