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

package org.apache.james.imap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.DefaultMailboxTyper;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.imap.api.process.MailboxTyper;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.XListRequest;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.imap.message.response.XListResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.google.inject.Guice;

import reactor.core.publisher.Flux;

class XListProcessorInjectionTest {
    private final Username username = Username.of("bob@example.com");
    private final MailboxManager mailboxManager = mock(MailboxManager.class);
    private final MailboxSession mailboxSession = mock(MailboxSession.class);
    private final ImapSession session = mock(ImapSession.class);
    private XListProcessor processor;

    @BeforeEach
    void setUp() {
        when(mailboxSession.getUser()).thenReturn(username);
        when(mailboxSession.getPathDelimiter()).thenReturn('.');
        when(session.getMailboxSession()).thenReturn(mailboxSession);
        processor = Guice.createInjector(binder -> {
            binder.bind(MailboxManager.class).toInstance(mailboxManager);
            binder.bind(SubscriptionManager.class).toInstance(mock(SubscriptionManager.class));
            binder.bind(StatusResponseFactory.class).toInstance(new UnpooledStatusResponseFactory());
            binder.bind(MetricFactory.class).toInstance(mock(MetricFactory.class));
            binder.bind(PathConverter.Factory.class).toInstance(PathConverter.Factory.DEFAULT);
            binder.bind(MailboxTyper.class).to(DefaultMailboxTyper.class);
        }).getInstance(XListProcessor.class);
    }

    @Test
    void injectedProcessorShouldAdvertiseXList() {
        assertThat(processor.getImplementedCapabilities(session)).contains(ImapConstants.SUPPORTS_XLIST);
    }

    @ParameterizedTest
    @CsvSource({"x-custom,OTHER", "x-,OTHER", "X-custom,OTHER", "Sent,SENT", "Spam,SPAM"})
    void injectedProcessorShouldListMailboxWithItsType(String name, MailboxType expected) {
        MailboxMetaData metaData = mock(MailboxMetaData.class);
        when(metaData.getPath()).thenReturn(MailboxPath.forUser(username, name));
        when(metaData.inferiors()).thenReturn(MailboxMetaData.Children.HAS_NO_CHILDREN);
        when(metaData.getSelectability()).thenReturn(MailboxMetaData.Selectability.NONE);
        when(metaData.getHierarchyDelimiter()).thenReturn('.');
        when(mailboxManager.search(any(), eq(MailboxManager.MailboxSearchFetchType.Minimal), eq(mailboxSession)))
            .thenReturn(Flux.just(metaData));
        List<ImapResponseMessage> responses = new ArrayList<>();

        ImapProcessor.Responder responder = mock(ImapProcessor.Responder.class);
        doAnswer(invocation -> {
            responses.add(invocation.getArgument(0));
            return null;
        }).when(responder).respond(any());

        processor.processRequestReactive(new XListRequest("", name, new Tag("x1")), session, responder).block();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0)).isInstanceOfSatisfying(XListResponse.class, response -> {
            assertThat(response.getName()).isEqualTo(name);
            assertThat(response.getType()).isEqualTo(expected);
            assertThat(response.getTypeAsString()).isEqualTo(expected.getAttributeName());
        });
        assertThat(responses.get(1)).isInstanceOfSatisfying(StatusResponse.class, response -> {
            assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.OK);
            assertThat(response.getTag()).isEqualTo(new Tag("x1"));
        });
    }
}
