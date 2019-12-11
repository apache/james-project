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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.SetQuotaRequest;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class SetQuotaProcessorTest {
    private SetQuotaProcessor testee;
    private ImapSession mockedImapSession;
    private ImapProcessor.Responder mockedResponder;
    private MailboxManager mockedMailboxManager;
    private MailboxSession mailboxSession;

    @Before
    public void setUp() {
        mailboxSession = MailboxSessionUtil.create(Username.of("plop"));
        UnpooledStatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        mockedImapSession = mock(ImapSession.class);
        mockedResponder = mock(ImapProcessor.Responder.class);
        mockedMailboxManager = mock(MailboxManager.class);
        testee = new SetQuotaProcessor(mock(ImapProcessor.class), mockedMailboxManager,
            statusResponseFactory, new NoopMetricFactory());
    }

    @Test
    public void processorShouldWorkOnNoRights() {
        SetQuotaRequest setQuotaRequest = new SetQuotaRequest(TAG, ImapCommand.anyStateCommand("Name"), "quotaRoot");

        when(mockedImapSession.getState()).thenReturn(ImapSessionState.AUTHENTICATED);
        when(mockedImapSession.getMailboxSession())
            .thenReturn(mailboxSession);

        testee.doProcess(setQuotaRequest, mockedResponder, mockedImapSession);

        ArgumentCaptor<ImapResponseMessage> imapResponseMessageArgumentCaptor = ArgumentCaptor.forClass(ImapResponseMessage.class);
        verify(mockedResponder).respond(imapResponseMessageArgumentCaptor.capture());
        assertThat(imapResponseMessageArgumentCaptor.getAllValues())
            .hasSize(1)
            .allMatch(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
        verifyNoMoreInteractions(mockedResponder);
    }

}
