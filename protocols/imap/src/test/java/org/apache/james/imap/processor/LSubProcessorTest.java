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

import static org.apache.james.imap.ImapFixture.TAG;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.LsubRequest;
import org.apache.james.imap.message.response.LSubResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class LSubProcessorTest {
    public static final Username USER = Username.of("test");

    private static final MailboxPath ROOT = MailboxPath.forUser(USER, "ROOT");
    
    private static final char HIERARCHY_DELIMITER = '.';

    private static final MailboxPath PARENT = ROOT.child("PARENT", HIERARCHY_DELIMITER);

    private static final MailboxPath CHILD_ONE = PARENT.child("CHILD_ONE", HIERARCHY_DELIMITER);

    private static final MailboxPath CHILD_TWO = PARENT.child("CHILD_TWO", HIERARCHY_DELIMITER);

    private static final MailboxPath MAILBOX_C = MailboxPath.forUser(USER, "C.MAILBOX");

    private static final MailboxPath MAILBOX_B = MailboxPath.forUser(USER, "B.MAILBOX");

    private static final MailboxPath MAILBOX_A = MailboxPath.forUser(USER, "A.MAILBOX");


    LSubProcessor processor;
    SubscriptionManager manager;
    ImapProcessor.Responder responder;
    MailboxMetaData result;
    ImapSession session;
    MailboxSession mailboxSession;
    StatusResponseFactory serverResponseFactory;
    StatusResponse statusResponse;
    Collection<MailboxPath> subscriptions;
    private ImapProcessor.Responder responderImpl;

    @BeforeEach
    void setUp() throws Exception {
        subscriptions = new ArrayList<>();
        serverResponseFactory = mock(StatusResponseFactory.class);
        session = new FakeImapSession();
        responder = mock(ImapProcessor.Responder.class);
        result = mock(MailboxMetaData.class);
        statusResponse = mock(StatusResponse.class);
        responderImpl = responder;
        manager =  mock(SubscriptionManager.class);
        mailboxSession = MailboxSessionUtil.create(USER);
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.manageProcessing(any(), any())).thenAnswer((Answer<Mono>) invocation -> {
            Object[] args = invocation.getArguments();
            return (Mono) args[0];
        });
        processor = new LSubProcessor(mailboxManager, manager, serverResponseFactory, new RecordingMetricFactory(), PathConverter.Factory.DEFAULT);
        session.setMailboxSession(mailboxSession);
    }

    @Test
    void testShouldRespondToRegexWithSubscribedMailboxes()
            throws Exception {
        subscriptions.add(MAILBOX_A);
        subscriptions.add(MAILBOX_B);
        subscriptions.add(MAILBOX_C);
        subscriptions.add(CHILD_ONE);
        subscriptions.add(CHILD_TWO);

        expectSubscriptions();
        when(serverResponseFactory.taggedOk(eq(TAG), same(ImapConstants.LSUB_COMMAND), eq(HumanReadableText.COMPLETED)))
            .thenReturn(statusResponse);

        LsubRequest request = new LsubRequest("", PARENT.getName()
                + HIERARCHY_DELIMITER + "%", TAG);
        processor.processRequest(request, session, responderImpl);

        verify(responder).respond(new LSubResponse(CHILD_ONE.getName(), false, HIERARCHY_DELIMITER));
        verify(responder).respond(new LSubResponse(CHILD_TWO.getName(), false, HIERARCHY_DELIMITER));
        verify(responder).respond(statusResponse);
    }

    @Test
    void testShouldRespondNoSelectToRegexWithParentsOfSubscribedMailboxes()
            throws Exception {
        subscriptions.add(MAILBOX_A);
        subscriptions.add(MAILBOX_B);
        subscriptions.add(MAILBOX_C);
        subscriptions.add(CHILD_ONE);
        subscriptions.add(CHILD_TWO);

        expectSubscriptions();
        when(serverResponseFactory.taggedOk(eq(TAG), same(ImapConstants.LSUB_COMMAND), eq(HumanReadableText.COMPLETED)))
            .thenReturn(statusResponse);

        LsubRequest request = new LsubRequest("", ROOT.getName()
                + HIERARCHY_DELIMITER + "%", TAG);
        processor.processRequest(request, session, responderImpl);

        verify(responder).respond(new LSubResponse(PARENT.getName(), true, HIERARCHY_DELIMITER));
        verify(responder).respond(statusResponse);
    }

    @Test
    void testShouldRespondSelectToRegexWithParentOfSubscribedMailboxesWhenParentSubscribed()
            throws Exception {
        subscriptions.add(MAILBOX_A);
        subscriptions.add(MAILBOX_B);
        subscriptions.add(MAILBOX_C);
        subscriptions.add(PARENT);
        subscriptions.add(CHILD_ONE);
        subscriptions.add(CHILD_TWO);

        expectSubscriptions();
        when(serverResponseFactory.taggedOk(eq(TAG), same(ImapConstants.LSUB_COMMAND), eq(HumanReadableText.COMPLETED)))
            .thenReturn(statusResponse);

        LsubRequest request = new LsubRequest("", ROOT.getName()
                + HIERARCHY_DELIMITER + "%", TAG);
        processor.processRequest(request, session, responderImpl);

        verify(responder).respond(new LSubResponse(PARENT.getName(), false, HIERARCHY_DELIMITER));
        verify(responder).respond(statusResponse);
    }

    @Test
    void testSelectAll() throws Exception {
        subscriptions.add(MAILBOX_A);
        subscriptions.add(MAILBOX_B);
        subscriptions.add(MAILBOX_C);
        expectSubscriptions();
        when(serverResponseFactory.taggedOk(eq(TAG), same(ImapConstants.LSUB_COMMAND), eq(HumanReadableText.COMPLETED)))
            .thenReturn(statusResponse);

        LsubRequest request = new LsubRequest("", "*", TAG);
        processor.processRequest(request, session, responderImpl);

        verify(responder).respond(new LSubResponse(MAILBOX_A.getName(), false, HIERARCHY_DELIMITER));
        verify(responder).respond(new LSubResponse(MAILBOX_B.getName(), false, HIERARCHY_DELIMITER));
        verify(responder).respond(new LSubResponse(MAILBOX_C.getName(), false, HIERARCHY_DELIMITER));
        verify(responder).respond(statusResponse);
    }

    private void expectSubscriptions() throws Exception {
        when(manager.subscriptionsReactive(mailboxSession)).thenReturn(Flux.fromIterable(subscriptions));
    }
}
