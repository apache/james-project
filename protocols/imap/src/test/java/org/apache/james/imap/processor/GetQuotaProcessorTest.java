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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.GetQuotaRequest;
import org.apache.james.imap.message.response.QuotaResponse;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.google.common.collect.ImmutableList;

public class GetQuotaProcessorTest {

    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("plop", Optional.empty());
    private static final Username PLOP = Username.of("plop");
    private static final MailboxPath MAILBOX_PATH = new MailboxPath("namespace", PLOP, "INBOX");
    private static final Quota<QuotaCountLimit, QuotaCountUsage> MESSAGE_QUOTA =
        Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(24)).computedLimit(QuotaCountLimit.count(1589)).build();
    private static final Quota<QuotaSizeLimit, QuotaSizeUsage> STORAGE_QUOTA =
        Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(240)).computedLimit(QuotaSizeLimit.size(15890)).build();

    private GetQuotaProcessor testee;
    private ImapSession imapSession;
    private ImapProcessor.Responder mockedResponder;
    private QuotaManager mockedQuotaManager;
    private QuotaRootResolver mockedQuotaRootResolver;
    private MailboxManager mockedMailboxManager;
    private MailboxSession mailboxSession;
    private Mailbox mailbox;

    @Before
    public void setUp() throws Exception {
        mailboxSession = MailboxSessionUtil.create(PLOP);
        UnpooledStatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        imapSession = new FakeImapSession();
        mockedQuotaManager = mock(QuotaManager.class);
        mockedQuotaRootResolver = mock(QuotaRootResolver.class);
        when(mockedQuotaRootResolver.fromString(eq(QUOTA_ROOT.getValue()))).thenReturn(QUOTA_ROOT);
        mockedResponder = mock(ImapProcessor.Responder.class);
        mockedMailboxManager = mock(MailboxManager.class);
        testee = new GetQuotaProcessor(mock(ImapProcessor.class), mockedMailboxManager,
            statusResponseFactory, mockedQuotaManager, mockedQuotaRootResolver, new NoopMetricFactory());
        mailbox = mock(Mailbox.class);
        when(mailbox.generateAssociatedPath()).thenReturn(MAILBOX_PATH);
        imapSession.authenticated();
        imapSession.setMailboxSession(mailboxSession);
    }

    @Test
    public void processorShouldWorkOnValidRights() throws Exception {
        GetQuotaRequest getQuotaRequest = new GetQuotaRequest(TAG, ImapCommand.anyStateCommand("Name"), QUOTA_ROOT.getValue());

        when(mockedQuotaRootResolver.retrieveAssociatedMailboxes(QUOTA_ROOT, mailboxSession))
            .thenReturn(ImmutableList.of(mailbox));
        when(mockedMailboxManager.hasRight(MAILBOX_PATH, MailboxACL.Right.Read, mailboxSession))
            .thenReturn(true);
        when(mockedQuotaManager.getMessageQuota(QUOTA_ROOT)).thenReturn(MESSAGE_QUOTA);
        when(mockedQuotaManager.getStorageQuota(QUOTA_ROOT)).thenReturn(STORAGE_QUOTA);

        QuotaResponse storageQuotaResponse = new QuotaResponse("STORAGE", "plop", STORAGE_QUOTA);
        QuotaResponse messageQuotaResponse = new QuotaResponse("MESSAGE", "plop", MESSAGE_QUOTA);

        testee.doProcess(getQuotaRequest, mockedResponder, imapSession);

        ArgumentCaptor<ImapResponseMessage> argumentCaptor = ArgumentCaptor.forClass(ImapResponseMessage.class);
        verify(mockedResponder, times(3)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(mockedResponder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(3)
            .contains(storageQuotaResponse, messageQuotaResponse);
        assertThat(argumentCaptor.getAllValues().get(2))
            .matches(StatusResponseTypeMatcher.OK_RESPONSE_MATCHER::matches);
    }

    @Test
    public void processorShouldWorkOnExceptionThrown() throws Exception {
        GetQuotaRequest getQuotaRequest = new GetQuotaRequest(TAG, ImapCommand.anyStateCommand("Name"), QUOTA_ROOT.getValue());

        when(mockedQuotaRootResolver.retrieveAssociatedMailboxes(QUOTA_ROOT, mailboxSession))
            .thenReturn(ImmutableList.of(mailbox));
        when(mockedMailboxManager.hasRight(MAILBOX_PATH, MailboxACL.Right.Read, mailboxSession))
            .thenReturn(true);
        when(mockedQuotaManager.getMessageQuota(QUOTA_ROOT)).thenThrow(new MailboxException());
        when(mockedQuotaManager.getStorageQuota(QUOTA_ROOT)).thenReturn(STORAGE_QUOTA);

        testee.doProcess(getQuotaRequest, mockedResponder, imapSession);

        ArgumentCaptor<ImapResponseMessage> argumentCaptor = ArgumentCaptor.forClass(ImapResponseMessage.class);
        verify(mockedResponder).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(mockedResponder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1)
            .allMatch(StatusResponseTypeMatcher.BAD_RESPONSE_MATCHER::matches);
    }

    @Test
    public void processorShouldWorkOnNoRights() throws Exception {
        GetQuotaRequest getQuotaRequest = new GetQuotaRequest(TAG, ImapCommand.anyStateCommand("Name"), QUOTA_ROOT.getValue());

        when(mockedQuotaRootResolver.retrieveAssociatedMailboxes(QUOTA_ROOT, mailboxSession))
            .thenReturn(ImmutableList.of(mailbox));
        when(mockedMailboxManager.hasRight(MAILBOX_PATH, MailboxACL.Right.Read, mailboxSession))
            .thenReturn(false);

        testee.doProcess(getQuotaRequest, mockedResponder, imapSession);

        ArgumentCaptor<ImapResponseMessage> argumentCaptor = ArgumentCaptor.forClass(ImapResponseMessage.class);
        verify(mockedResponder).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(mockedResponder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1)
            .allMatch(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }
}
