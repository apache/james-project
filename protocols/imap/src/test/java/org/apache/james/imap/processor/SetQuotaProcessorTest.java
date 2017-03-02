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

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.SetQuotaRequest;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class SetQuotaProcessorTest {

    private SetQuotaProcessor testee;
    private Mockery mockery;
    private ImapSession mockedImapSession;
    private ImapProcessor.Responder mockedResponder;
    private MailboxManager mockedMailboxManager;
    private MailboxSession mailboxSession;

    @Before
    public void setUp() {
        mailboxSession = new MockMailboxSession("plop");
        mockery = new JUnit4Mockery();
        UnpooledStatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        mockedImapSession = mockery.mock(ImapSession.class);
        mockedResponder = mockery.mock(ImapProcessor.Responder.class);
        mockedMailboxManager = mockery.mock(MailboxManager.class);
        testee = new SetQuotaProcessor(mockery.mock(ImapProcessor.class), mockedMailboxManager,
            statusResponseFactory, new NoopMetricFactory());
    }

    @Test
    public void processorShouldWorkOnNoRights() throws Exception {
        SetQuotaRequest setQuotaRequest = new SetQuotaRequest("A004", ImapCommand.anyStateCommand("Name"), "quotaRoot");
        Expectations expectations = new Expectations();

        expectations.allowing(mockedImapSession).getState();
        expectations.will(Expectations.returnValue(ImapSessionState.AUTHENTICATED));

        expectations.allowing(mockedImapSession).getAttribute(expectations.with(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY));
        expectations.will(Expectations.returnValue(mailboxSession));

        expectations.allowing(mockedMailboxManager).startProcessingRequest(expectations.with(mailboxSession));

        expectations.allowing(mockedMailboxManager).endProcessingRequest(expectations.with(mailboxSession));

        mockery.checking(expectations);

        mockery.checking(new Expectations() {
            {
                oneOf(mockedResponder).respond(with(new StatusResponseTypeMatcher(StatusResponse.Type.NO)));
            }
        });

        testee.doProcess(setQuotaRequest, mockedResponder, mockedImapSession);
    }

}
