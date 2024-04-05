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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.apache.james.jmap.draft.model.GetMailboxesRequest;
import org.apache.james.jmap.draft.model.MailboxCreationId;
import org.apache.james.jmap.methods.JmapRequest;
import org.apache.james.jmap.methods.JmapResponse;
import org.apache.james.jmap.model.MethodCallId;
import org.apache.james.jmap.draft.model.SetMailboxesRequest;
import org.apache.james.jmap.draft.model.SetMailboxesResponse;
import org.apache.james.jmap.draft.model.mailbox.Mailbox;
import org.apache.james.jmap.draft.model.mailbox.MailboxCreateRequest;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class SetMailboxesMethodTest {

    private static final ImmutableSet<SetMailboxesProcessor> NO_PROCESSOR = ImmutableSet.of();
    private static final DefaultMetricFactory TIME_METRIC_FACTORY = new DefaultMetricFactory();

    @Test
    public void requestHandledShouldBeSetMailboxes() {
        assertThat(new SetMailboxesMethod(NO_PROCESSOR, TIME_METRIC_FACTORY).requestHandled().getName()).isEqualTo("setMailboxes");
    }

    @Test
    public void requestTypeShouldBeSetMailboxes() {
        assertThat(new SetMailboxesMethod(NO_PROCESSOR, TIME_METRIC_FACTORY).requestType()).isEqualTo(SetMailboxesRequest.class);
    }

    @Test
    public void processShouldThrowWhenNullJmapRequest() {
        MailboxSession session = mock(MailboxSession.class);
        JmapRequest nullJmapRequest = null;
        assertThatThrownBy(() -> new SetMailboxesMethod(NO_PROCESSOR, TIME_METRIC_FACTORY).processToStream(nullJmapRequest, MethodCallId.of("methodCallId"), session))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenNullMethodCallId() {
        MailboxSession session = mock(MailboxSession.class);
        JmapRequest jmapRequest = mock(JmapRequest.class);
        MethodCallId nullMethodCallId = null;
        assertThatThrownBy(() -> new SetMailboxesMethod(NO_PROCESSOR, TIME_METRIC_FACTORY).processToStream(jmapRequest, nullMethodCallId, session))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenNullMailboxSession() {
        MailboxSession nullMailboxSession = null;
        JmapRequest jmapRequest = mock(JmapRequest.class);
        assertThatThrownBy(() -> new SetMailboxesMethod(NO_PROCESSOR, TIME_METRIC_FACTORY).processToStream(jmapRequest, MethodCallId.of("methodCallId"), nullMailboxSession))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenJmapRequestTypeMismatch() {
        MailboxSession session = mock(MailboxSession.class);
        JmapRequest getMailboxesRequest = GetMailboxesRequest.builder().build();
        assertThatThrownBy(() -> new SetMailboxesMethod(NO_PROCESSOR, TIME_METRIC_FACTORY).processToStream(getMailboxesRequest, MethodCallId.of("methodCallId"), session))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void processShouldCallCreatorProcessorWhenCreationRequest() {
        MailboxCreationId creationId = MailboxCreationId.of("create-id01");
        MailboxCreateRequest fooFolder = MailboxCreateRequest.builder().name("fooFolder").build();
        SetMailboxesRequest creationRequest = SetMailboxesRequest.builder().create(creationId, fooFolder).build();

        Mailbox createdfooFolder = Mailbox.builder().name("fooFolder").id(InMemoryId.of(123)).build();
        SetMailboxesResponse creationResponse = SetMailboxesResponse.builder().created(creationId, createdfooFolder).build();
        JmapResponse jmapResponse = JmapResponse.builder()
            .response(creationResponse)
            .methodCallId(MethodCallId.of("methodCallId"))
            .responseName(SetMailboxesMethod.RESPONSE_NAME)
            .build();

        MailboxSession session = mock(MailboxSession.class);
        SetMailboxesProcessor creatorProcessor = mock(SetMailboxesProcessor.class);
        when(creatorProcessor.processReactive(creationRequest, session)).thenReturn(Mono.just(creationResponse));

        Stream<JmapResponse> actual =
            new SetMailboxesMethod(ImmutableSet.of(creatorProcessor), TIME_METRIC_FACTORY)
                    .process(creationRequest, MethodCallId.of("methodCallId"), session)
            .toStream();

        assertThat(actual).contains(jmapResponse);
    }

    @Test
    public void processShouldCallDestructorProcessorWhenCreationRequest() {
        ImmutableList<MailboxId> deletions = ImmutableList.of(InMemoryId.of(1));
        SetMailboxesRequest destructionRequest = SetMailboxesRequest.builder().destroy(deletions).build();

        SetMailboxesResponse destructionResponse = SetMailboxesResponse.builder().destroyed(deletions).build();
        JmapResponse jmapResponse = JmapResponse.builder()
            .response(destructionResponse)
            .methodCallId(MethodCallId.of("methodCallId"))
            .responseName(SetMailboxesMethod.RESPONSE_NAME)
            .build();

        MailboxSession session = mock(MailboxSession.class);
        SetMailboxesProcessor destructorProcessor = mock(SetMailboxesProcessor.class);
        when(destructorProcessor.processReactive(destructionRequest, session)).thenReturn(Mono.just(destructionResponse));

        Stream<JmapResponse> actual =
            new SetMailboxesMethod(ImmutableSet.of(destructorProcessor), TIME_METRIC_FACTORY)
                    .process(destructionRequest, MethodCallId.of("methodCallId"), session)
            .toStream();

        assertThat(actual).contains(jmapResponse);
    }
}
