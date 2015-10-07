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

import com.google.common.collect.Lists;
import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.GetQuotaRequest;
import org.apache.james.imap.message.response.QuotaResponse;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.quota.QuotaImpl;
import org.apache.james.mailbox.store.quota.QuotaRootImpl;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class GetQuotaProcessorTest {

    private static final QuotaRoot QUOTA_ROOT = QuotaRootImpl.quotaRoot("plop");
    public static final MailboxPath MAILBOX_PATH = new MailboxPath("namespace", "plop", "INBOX");
    public static final Quota MESSAGE_QUOTA = QuotaImpl.quota(24, 1589);
    public static final Quota STORAGE_QUOTA = QuotaImpl.quota(240, 15890);

    private GetQuotaProcessor testee;
    private Mockery mockery;
    private ImapSession mockedImapSession;
    private ImapProcessor.Responder mockedResponder;
    private QuotaManager mockedQuotaManager;
    private QuotaRootResolver mockedQuotaRootResolver;
    private MailboxManager mockedMailboxManager;
    private MailboxSession mailboxSession;

    @Before
    public void setUp() {
        mailboxSession = new MockMailboxSession("plop");
        mockery = new JUnit4Mockery();
        UnpooledStatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        mockedImapSession = mockery.mock(ImapSession.class);
        mockedQuotaManager = mockery.mock(QuotaManager.class);
        mockedQuotaRootResolver = mockery.mock(QuotaRootResolver.class);
        mockedResponder = mockery.mock(ImapProcessor.Responder.class);
        mockedMailboxManager = mockery.mock(MailboxManager.class);
        testee = new GetQuotaProcessor(mockery.mock(ImapProcessor.class), mockedMailboxManager,
            statusResponseFactory, mockedQuotaManager, mockedQuotaRootResolver);
    }

    @Test
    public void processorShouldWorkOnValidRights() throws Exception {
        GetQuotaRequest getQuotaRequest = new GetQuotaRequest("A004", ImapCommand.anyStateCommand("Name"), "quotaRoot");
        Expectations expectations = new Expectations();

        expectations.allowing(mockedImapSession).getState();
        expectations.will(Expectations.returnValue(ImapSessionState.AUTHENTICATED));

        expectations.allowing(mockedImapSession).getAttribute(expectations.with(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY));
        expectations.will(Expectations.returnValue(mailboxSession));

        expectations.allowing(mockedQuotaRootResolver).createQuotaRoot(expectations.with("quotaRoot"));
        expectations.will(Expectations.returnValue(QUOTA_ROOT));

        expectations.allowing(mockedQuotaRootResolver).retrieveAssociatedMailboxes(expectations.with(QUOTA_ROOT), expectations.with(mailboxSession));
        expectations.will(Expectations.returnValue(Lists.newArrayList(MAILBOX_PATH)));

        expectations.allowing(mockedMailboxManager).hasRight(expectations.with(MAILBOX_PATH),
            expectations.with(SimpleMailboxACL.Rfc4314Rights.r_Read_RIGHT), expectations.with(mailboxSession));
        expectations.will(Expectations.returnValue(true));

        expectations.allowing(mockedQuotaManager).getMessageQuota(expectations.with(QUOTA_ROOT));
        expectations.will(Expectations.returnValue(MESSAGE_QUOTA));

        expectations.allowing(mockedQuotaManager).getStorageQuota(expectations.with(QUOTA_ROOT));
        expectations.will(Expectations.returnValue(STORAGE_QUOTA));

        expectations.allowing(mockedMailboxManager).startProcessingRequest(expectations.with(mailboxSession));

        expectations.allowing(mockedMailboxManager).endProcessingRequest(expectations.with(mailboxSession));

        final QuotaResponse storageQuotaResponse = new QuotaResponse("STORAGE", "plop", STORAGE_QUOTA);
        final QuotaResponse messageQuotaResponse = new QuotaResponse("MESSAGE", "plop", MESSAGE_QUOTA);

        mockery.checking(expectations);

        mockery.checking(new Expectations() {
            {
                oneOf(mockedResponder).respond(with(equal(storageQuotaResponse)));
                oneOf(mockedResponder).respond(with(equal(messageQuotaResponse)));
                oneOf(mockedResponder).respond(with(new StatusResponseTypeMatcher(StatusResponse.Type.OK)));
            }
        });

        testee.doProcess(getQuotaRequest, mockedResponder, mockedImapSession);
    }

    @Test
    public void processorShouldWorkOnExceptionThrown() throws Exception {
        GetQuotaRequest getQuotaRequest = new GetQuotaRequest("A004", ImapCommand.anyStateCommand("Name"), "quotaRoot");
        Expectations expectations = new Expectations();

        expectations.allowing(mockedImapSession).getState();
        expectations.will(Expectations.returnValue(ImapSessionState.AUTHENTICATED));

        expectations.allowing(mockedImapSession).getAttribute(expectations.with(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY));
        expectations.will(Expectations.returnValue(mailboxSession));

        expectations.allowing(mockedQuotaRootResolver).createQuotaRoot(expectations.with("quotaRoot"));
        expectations.will(Expectations.returnValue(QUOTA_ROOT));

        expectations.allowing(mockedQuotaRootResolver).retrieveAssociatedMailboxes(expectations.with(QUOTA_ROOT), expectations.with(mailboxSession));
        expectations.will(Expectations.returnValue(Lists.newArrayList(MAILBOX_PATH)));

        expectations.allowing(mockedMailboxManager).hasRight(expectations.with(MAILBOX_PATH),
            expectations.with(SimpleMailboxACL.Rfc4314Rights.r_Read_RIGHT), expectations.with(mailboxSession));
        expectations.will(Expectations.returnValue(true));

        expectations.allowing(mockedQuotaManager).getMessageQuota(expectations.with(QUOTA_ROOT));
        expectations.will(Expectations.throwException(new MailboxException()));

        expectations.allowing(mockedMailboxManager).startProcessingRequest(expectations.with(mailboxSession));

        expectations.allowing(mockedMailboxManager).endProcessingRequest(expectations.with(mailboxSession));

        mockery.checking(expectations);

        mockery.checking(new Expectations() {
            {
                oneOf(mockedResponder).respond(with(new StatusResponseTypeMatcher(StatusResponse.Type.BAD)));
            }
        });

        testee.doProcess(getQuotaRequest, mockedResponder, mockedImapSession);
    }

    @Test
    public void processorShouldWorkOnNoRights() throws Exception {
        GetQuotaRequest getQuotaRequest = new GetQuotaRequest("A004", ImapCommand.anyStateCommand("Name"), "quotaRoot");
        Expectations expectations = new Expectations();

        expectations.allowing(mockedImapSession).getState();
        expectations.will(Expectations.returnValue(ImapSessionState.AUTHENTICATED));

        expectations.allowing(mockedImapSession).getAttribute(expectations.with(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY));
        expectations.will(Expectations.returnValue(mailboxSession));

        expectations.allowing(mockedQuotaRootResolver).createQuotaRoot(expectations.with("quotaRoot"));
        expectations.will(Expectations.returnValue(QUOTA_ROOT));

        expectations.allowing(mockedQuotaRootResolver).retrieveAssociatedMailboxes(expectations.with(QUOTA_ROOT), expectations.with(mailboxSession));
        expectations.will(Expectations.returnValue(Lists.newArrayList(MAILBOX_PATH)));

        expectations.allowing(mockedMailboxManager).hasRight(expectations.with(MAILBOX_PATH),
            expectations.with(SimpleMailboxACL.Rfc4314Rights.r_Read_RIGHT), expectations.with(mailboxSession));
        expectations.will(Expectations.returnValue(false));

        expectations.allowing(mockedMailboxManager).startProcessingRequest(expectations.with(mailboxSession));

        expectations.allowing(mockedMailboxManager).endProcessingRequest(expectations.with(mailboxSession));

        mockery.checking(expectations);

        mockery.checking(new Expectations() {
            {
                oneOf(mockedResponder).respond(with(new StatusResponseTypeMatcher(StatusResponse.Type.NO)));
            }
        });

        testee.doProcess(getQuotaRequest, mockedResponder, mockedImapSession);
    }

}
