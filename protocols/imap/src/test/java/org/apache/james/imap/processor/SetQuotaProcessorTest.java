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

import org.apache.james.core.Username;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.SetQuotaRequest;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class SetQuotaProcessorTest {
    private SetQuotaProcessor testee;
    private FakeImapSession imapSession;
    private ImapProcessor.Responder mockedResponder;

    @Before
    public void setUp() {
        MailboxSession mailboxSession = MailboxSessionUtil.create(Username.of("plop"));
        UnpooledStatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        imapSession = new FakeImapSession();
        mockedResponder = mock(ImapProcessor.Responder.class);
        testee = new SetQuotaProcessor(mock(ImapProcessor.class), mock(MailboxManager.class),
            statusResponseFactory, new RecordingMetricFactory());
        imapSession.authenticated();
        imapSession.setMailboxSession(mailboxSession);
    }

    @Test
    public void processorShouldWorkOnNoRights() {
        SetQuotaRequest setQuotaRequest = new SetQuotaRequest(TAG, "quotaRoot");

        testee.doProcess(setQuotaRequest, mockedResponder, imapSession);

        ArgumentCaptor<ImapResponseMessage> imapResponseMessageArgumentCaptor = ArgumentCaptor.forClass(ImapResponseMessage.class);
        verify(mockedResponder).respond(imapResponseMessageArgumentCaptor.capture());
        assertThat(imapResponseMessageArgumentCaptor.getAllValues())
            .hasSize(1)
            .allMatch(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
        verifyNoMoreInteractions(mockedResponder);
    }

}
