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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.LsubRequest;
import org.apache.james.imap.message.response.LSubResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.Before;
import org.junit.Test;

public class LSubProcessorTest {

    private static final String ROOT = "ROOT";
    
    private static final char HIERARCHY_DELIMITER = '.';

    private static final String PARENT = ROOT
            + HIERARCHY_DELIMITER + "PARENT";

    private static final String CHILD_ONE = PARENT
            + HIERARCHY_DELIMITER + "CHILD_ONE";

    private static final String CHILD_TWO = PARENT
            + HIERARCHY_DELIMITER + "CHILD_TWO";

    private static final String MAILBOX_C = "C.MAILBOX";

    private static final String MAILBOX_B = "B.MAILBOX";

    private static final String MAILBOX_A = "A.MAILBOX";

    public static final Username USER = Username.of("test");

    LSubProcessor processor;
    ImapProcessor next;
    SubscriptionManager manager;
    ImapProcessor.Responder responder;
    MailboxMetaData result;
    ImapSession session;
    MailboxSession mailboxSession;
    StatusResponseFactory serverResponseFactory;
    StatusResponse statusResponse;
    Collection<String> subscriptions;
    ImapCommand command;
    private ImapProcessor.Responder responderImpl;

    @Before
    public void setUp() throws Exception {
        subscriptions = new ArrayList<>();
        serverResponseFactory = mock(StatusResponseFactory.class);
        session = mock(ImapSession.class);
        command = ImapCommand.anyStateCommand("Command");
        next = mock(ImapProcessor.class);
        responder = mock(ImapProcessor.Responder.class);
        result = mock(MailboxMetaData.class);
        statusResponse = mock(StatusResponse.class);
        responderImpl = responder;
        manager =  mock(SubscriptionManager.class);
        mailboxSession = MailboxSessionUtil.create(USER);
        processor = new LSubProcessor(next, mock(MailboxManager.class), manager, serverResponseFactory, new NoopMetricFactory());
    }

    @Test
    public void testShouldRespondToRegexWithSubscribedMailboxes()
            throws Exception {
        subscriptions.add(MAILBOX_A);
        subscriptions.add(MAILBOX_B);
        subscriptions.add(MAILBOX_C);
        subscriptions.add(CHILD_ONE);
        subscriptions.add(CHILD_TWO);

        expectSubscriptions();
        when(serverResponseFactory.taggedOk(eq(TAG), same(command), eq(HumanReadableText.COMPLETED)))
            .thenReturn(statusResponse);

        LsubRequest request = new LsubRequest(command, "", PARENT
                + HIERARCHY_DELIMITER + "%", TAG);
        processor.doProcessRequest(request, session, responderImpl);

        verify(responder).respond(new LSubResponse(CHILD_ONE, false, HIERARCHY_DELIMITER));
        verify(responder).respond(new LSubResponse(CHILD_TWO, false, HIERARCHY_DELIMITER));
        verify(responder).respond(statusResponse);
    }

    @Test
    public void testShouldRespondNoSelectToRegexWithParentsOfSubscribedMailboxes()
            throws Exception {
        subscriptions.add(MAILBOX_A);
        subscriptions.add(MAILBOX_B);
        subscriptions.add(MAILBOX_C);
        subscriptions.add(CHILD_ONE);
        subscriptions.add(CHILD_TWO);

        expectSubscriptions();
        when(serverResponseFactory.taggedOk(eq(TAG), same(command), eq(HumanReadableText.COMPLETED)))
            .thenReturn(statusResponse);

        LsubRequest request = new LsubRequest(command, "", ROOT
                + HIERARCHY_DELIMITER + "%", TAG);
        processor.doProcessRequest(request, session, responderImpl);

        verify(responder).respond(new LSubResponse(PARENT, true, HIERARCHY_DELIMITER));
        verify(responder).respond(statusResponse);
    }

    @Test
    public void testShouldRespondSelectToRegexWithParentOfSubscribedMailboxesWhenParentSubscribed()
            throws Exception {
        subscriptions.add(MAILBOX_A);
        subscriptions.add(MAILBOX_B);
        subscriptions.add(MAILBOX_C);
        subscriptions.add(PARENT);
        subscriptions.add(CHILD_ONE);
        subscriptions.add(CHILD_TWO);

        expectSubscriptions();
        when(serverResponseFactory.taggedOk(eq(TAG), same(command), eq(HumanReadableText.COMPLETED)))
            .thenReturn(statusResponse);

        LsubRequest request = new LsubRequest(command, "", ROOT
                + HIERARCHY_DELIMITER + "%", TAG);
        processor.doProcessRequest(request, session, responderImpl);

        verify(responder).respond(new LSubResponse(PARENT, false, HIERARCHY_DELIMITER));
        verify(responder).respond(statusResponse);
    }

    @Test
    public void testSelectAll() throws Exception {
        subscriptions.add(MAILBOX_A);
        subscriptions.add(MAILBOX_B);
        subscriptions.add(MAILBOX_C);
        expectSubscriptions();
        when(serverResponseFactory.taggedOk(eq(TAG), same(command), eq(HumanReadableText.COMPLETED)))
            .thenReturn(statusResponse);

        LsubRequest request = new LsubRequest(command, "", "*", TAG);
        processor.doProcessRequest(request, session, responderImpl);

        verify(responder).respond(new LSubResponse(MAILBOX_A, false, HIERARCHY_DELIMITER));
        verify(responder).respond(new LSubResponse(MAILBOX_B, false, HIERARCHY_DELIMITER));
        verify(responder).respond(new LSubResponse(MAILBOX_C, false, HIERARCHY_DELIMITER));
        verify(responder).respond(statusResponse);
    }

    private void expectSubscriptions() throws Exception {
        when(session.getMailboxSession()).thenReturn(mailboxSession);
        when(manager.subscriptions(mailboxSession)).thenReturn(subscriptions);
    }
}
