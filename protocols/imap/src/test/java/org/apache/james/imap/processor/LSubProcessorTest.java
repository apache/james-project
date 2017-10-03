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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.LsubRequest;
import org.apache.james.imap.message.response.LSubResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
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

    private static final String TAG = "TAG";
    public static final MailboxSession.User USER = new MailboxSession.User() {

        /**
         * @see MailboxSession.User#getLocalePreferences()
         */
        public List<Locale> getLocalePreferences() {
            return new ArrayList<>();
        }

        /**
         * @see MailboxSession.User#getPassword()
         */
        public String getPassword() {
            return "test";
        }

        /**
         * @see MailboxSession.User#getUserName()
         */
        public String getUserName() {
            return "test";
        }

        @Override
        public boolean isSameUser(String username) {
            return "test".equalsIgnoreCase(username);
        }

    };

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

    private Mockery mockery = new JUnit4Mockery();
    
    @Before
    public void setUp() throws Exception {
        subscriptions = new ArrayList<>();
        serverResponseFactory = mockery.mock(StatusResponseFactory.class);
        session = mockery.mock(ImapSession.class);
        command = ImapCommand.anyStateCommand("Command");
        next = mockery.mock(ImapProcessor.class);
        responder = mockery.mock(ImapProcessor.Responder.class);
        result = mockery.mock(MailboxMetaData.class);
        statusResponse = mockery.mock(StatusResponse.class);
        responderImpl = responder;
        manager = mockery. mock(SubscriptionManager.class);
        mailboxSession = mockery.mock(MailboxSession.class);
        processor = new LSubProcessor(next, mockery.mock(MailboxManager.class), manager, serverResponseFactory, new NoopMetricFactory());
    }

    @Test
    public void testHierarchy() throws Exception {
        subscriptions.add(MAILBOX_A);
        subscriptions.add(MAILBOX_B);
        subscriptions.add(MAILBOX_C);

        mockery.checking(new Expectations() {{
            allowing(session).getAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY); will(returnValue(mailboxSession));
            allowing(mailboxSession).getPathDelimiter(); will(returnValue(HIERARCHY_DELIMITER));
            oneOf(responder).respond(with(
                    equal(new LSubResponse("", true, HIERARCHY_DELIMITER))));
        }});

        expectOk();

        LsubRequest request = new LsubRequest(command, "", "", TAG);
        processor.doProcessRequest(request, session, TAG, command, responderImpl);

    }

    @Test
    public void testShouldRespondToRegexWithSubscribedMailboxes()
            throws Exception {
        subscriptions.add(MAILBOX_A);
        subscriptions.add(MAILBOX_B);
        subscriptions.add(MAILBOX_C);
        subscriptions.add(CHILD_ONE);
        subscriptions.add(CHILD_TWO);

        mockery.checking(new Expectations() {{
            oneOf(mailboxSession).getUser(); will(returnValue(USER));
            oneOf(responder).respond(with(
                    equal(new LSubResponse(CHILD_ONE, false, HIERARCHY_DELIMITER))));
            oneOf(responder).respond(with(
                    equal(new LSubResponse(CHILD_TWO, false, HIERARCHY_DELIMITER))));
        }});
        
        expectSubscriptions();
        expectOk();

        LsubRequest request = new LsubRequest(command, "", PARENT
                + HIERARCHY_DELIMITER + "%", TAG);
        processor.doProcessRequest(request, session, TAG, command, responderImpl);

    }

    @Test
    public void testShouldRespondNoSelectToRegexWithParentsOfSubscribedMailboxes()
            throws Exception {
        subscriptions.add(MAILBOX_A);
        subscriptions.add(MAILBOX_B);
        subscriptions.add(MAILBOX_C);
        subscriptions.add(CHILD_ONE);
        subscriptions.add(CHILD_TWO);

        mockery.checking(new Expectations() {{
            oneOf(mailboxSession).getUser(); will(returnValue(USER));
            oneOf(responder).respond(with(
                    equal(new LSubResponse(PARENT, true, HIERARCHY_DELIMITER))));
        }});

        expectSubscriptions();
        expectOk();

        LsubRequest request = new LsubRequest(command, "", ROOT
                + HIERARCHY_DELIMITER + "%", TAG);
        processor.doProcessRequest(request, session, TAG, command, responderImpl);

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

        mockery.checking(new Expectations() {{
            oneOf(mailboxSession).getUser(); will(returnValue(USER));
            oneOf(responder).respond(with(
                    equal(new LSubResponse(PARENT, false, HIERARCHY_DELIMITER))));
        }});

        expectSubscriptions();
        expectOk();

        LsubRequest request = new LsubRequest(command, "", ROOT
                + HIERARCHY_DELIMITER + "%", TAG);
        processor.doProcessRequest(request, session, TAG, command, responderImpl);

    }

    @Test
    public void testSelectAll() throws Exception {
        mockery.checking(new Expectations() {{
            oneOf(mailboxSession).getUser(); will(returnValue(USER));
            oneOf(responder).respond(with(equal(
                    new LSubResponse(MAILBOX_A, false, HIERARCHY_DELIMITER))));
            oneOf(responder).respond(with(equal(
                    new LSubResponse(MAILBOX_B, false, HIERARCHY_DELIMITER))));
            oneOf(responder).respond(with(equal(
                    new LSubResponse(MAILBOX_C, false, HIERARCHY_DELIMITER))));
        }});
        subscriptions.add(MAILBOX_A);
        subscriptions.add(MAILBOX_B);
        subscriptions.add(MAILBOX_C);

        expectSubscriptions();
        expectOk();

        LsubRequest request = new LsubRequest(command, "", "*", TAG);
        processor.doProcessRequest(request, session, TAG, command, responderImpl);

    }

    
    private void expectOk() {
        mockery.checking(new Expectations() {{
            oneOf(serverResponseFactory).taggedOk(
                    with(equal(TAG)),
                    with(same(command)),
                    with(equal(HumanReadableText.COMPLETED)));will(returnValue(statusResponse));
            oneOf(responder).respond(with(same(statusResponse)));          
        }});
    }

    private void expectSubscriptions() throws Exception {
        mockery.checking(new Expectations() {{
            exactly(2).of(session).getAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY);
                    will(returnValue(mailboxSession));
                    allowing(mailboxSession).getPathDelimiter(); will(returnValue(HIERARCHY_DELIMITER));
            oneOf(mailboxSession).getUser(); will(returnValue(USER));
            oneOf(manager).subscriptions(with(same(mailboxSession)));will(returnValue(subscriptions));     
        }});
    }
}
