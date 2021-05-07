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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.james.jmap.draft.model.MailboxFactory;
import org.apache.james.jmap.draft.model.SetError;
import org.apache.james.jmap.draft.model.SetMailboxesRequest;
import org.apache.james.jmap.draft.model.SetMailboxesResponse;
import org.apache.james.jmap.draft.model.mailbox.Mailbox;
import org.apache.james.jmap.draft.model.mailbox.MailboxUpdateRequest;
import org.apache.james.jmap.draft.utils.MailboxUtils;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import reactor.core.publisher.Mono;

public class SetMailboxesUpdateProcessorTest {

    private MailboxManager mockedMailboxManager;
    private MailboxUtils mockedMailboxUtils;
    private MailboxFactory mockedMailboxFactory;
    private MailboxSession mockedMailboxSession;
    private SetMailboxesUpdateProcessor sut;

    @Before
    public void setup() {
        mockedMailboxManager = mock(MailboxManager.class);
        mockedMailboxUtils = mock(MailboxUtils.class);
        mockedMailboxFactory = mock(MailboxFactory.class);
        mockedMailboxSession = mock(MailboxSession.class);
        MetricFactory metricFactory = new RecordingMetricFactory();
        sut = new SetMailboxesUpdateProcessor(mockedMailboxUtils, mockedMailboxManager, mockedMailboxFactory, metricFactory);
    }

    @Test
    public void processShouldReturnNotUpdatedWhenMailboxExceptionOccured() throws Exception {
        // Given
        InMemoryId mailboxId = InMemoryId.of(1);
        InMemoryId newParentId = InMemoryId.of(2);
        SetMailboxesRequest request = SetMailboxesRequest.builder()
                .update(mailboxId, MailboxUpdateRequest.builder().parentId(newParentId).build())
                .build();
        Mailbox mailbox = Mailbox.builder().id(mailboxId).name("name").role(Optional.empty()).build();

        MailboxFactory.MailboxBuilder mockBuilder = mock(MailboxFactory.MailboxBuilder.class);
        when(mockBuilder.id(mailboxId))
            .thenReturn(mockBuilder);
        when(mockBuilder.session(mockedMailboxSession))
            .thenReturn(mockBuilder);
        when(mockBuilder.build())
            .thenReturn(Mono.just(mailbox));

        when(mockedMailboxFactory.builder())
            .thenReturn(mockBuilder);
        when(mockedMailboxManager.getMailbox(newParentId, mockedMailboxSession))
            .thenReturn(mock(MessageManager.class));
        when(mockedMailboxUtils.hasChildren(mailboxId, mockedMailboxSession))
            .thenThrow(new MailboxException());

        // When
        SetMailboxesResponse setMailboxesResponse = sut.process(request, mockedMailboxSession);

        // Then
        verify(mockBuilder, times(1)).id(Mockito.eq(mailboxId));
        verify(mockBuilder, times(1)).session(Mockito.eq(mockedMailboxSession));
        assertThat(setMailboxesResponse.getUpdated()).isEmpty();
        assertThat(setMailboxesResponse.getNotUpdated()).containsEntry(mailboxId, SetError.builder().type(SetError.Type.ERROR).description("An error occurred when updating the mailbox").build());
    }

    @Test
    public void requestChangedShouldReturnFalseWhenRequestValueAndStoreValueAreEmpty() throws Exception {
        assertThat(sut.requestChanged(Optional.<String>empty(), Optional.empty())).isFalse();
    }

    @Test
    public void requestChangedShouldReturnFalseWhenEmptyRequestMeansNoChanging() throws Exception {
        assertThat(sut.requestChanged(Optional.empty(), Optional.of("any"))).isFalse();
    }

    @Test
    public void requestChangedShouldReturnTrueWhenEmptyStoreValue() throws Exception {
        assertThat(sut.requestChanged(Optional.of("any"), Optional.empty())).isTrue();
    }

    @Test
    public void requestChangedShouldReturnTrueWhenRequestValueAndStoreValueAreNotTheSame() throws Exception {
        assertThat(sut.requestChanged(Optional.of("any"), Optional.of("other"))).isTrue();
    }

    @Test
    public void requestChangedShouldReturnFalseWhenRequestValueAndStoreValueAreTheSame() throws Exception {
        assertThat(sut.requestChanged(Optional.of("any"), Optional.of("any"))).isFalse();
    }

}
