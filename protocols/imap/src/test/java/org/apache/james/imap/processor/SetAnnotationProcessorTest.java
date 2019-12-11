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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.SetAnnotationRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.exception.AnnotationException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.ImmutableList;

public class SetAnnotationProcessorTest {
    private static final int FIRST_ELEMENT_INDEX = 0;

    @InjectMocks
    private SetAnnotationProcessor processor;

    private ImapProcessor mockNextProcessor;
    private MailboxManager mockMailboxManager;
    private StatusResponseFactory mockStatusResponseFactory;
    private ImapProcessor.Responder mockResponder;
    private FakeImapSession imapSession;
    private MailboxSession mockMailboxSession;

    private List<MailboxAnnotation> mailboxAnnotations;
    private StatusResponse okResponse;

    private MailboxPath inbox; 

    private SetAnnotationRequest request;
    private ArgumentCaptor<HumanReadableText> humanTextCaptor;
    
    private void initAndMockData() {
        okResponse = mock(StatusResponse.class);
        mockNextProcessor = mock(ImapProcessor.class);
        mockMailboxManager = mock(MailboxManager.class);
        mockStatusResponseFactory = mock(StatusResponseFactory.class);
        mockResponder = mock(ImapProcessor.Responder.class);
        imapSession = new FakeImapSession();

        Username username = Username.of("username");
        mockMailboxSession = MailboxSessionUtil.create(username);
        inbox = MailboxPath.inbox(username);
        mailboxAnnotations = ImmutableList.of(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/key"), "anyValue"));
        request = new SetAnnotationRequest(TAG, ImapCommand.anyStateCommand("Name"), ImapConstants.INBOX_NAME, mailboxAnnotations);
        humanTextCaptor = ArgumentCaptor.forClass(HumanReadableText.class);

        imapSession.authenticated();
        imapSession.setMailboxSession(mockMailboxSession);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        initAndMockData();
        processor = new SetAnnotationProcessor(mockNextProcessor, mockMailboxManager, mockStatusResponseFactory, new NoopMetricFactory());
    }
    
    @After
    public void tearDown() {
        processor = null;
    }

    @Test
    public void getImplementedCapabilitiesShouldContainSupportAnnotationWhenMailboxManagerHasAnnotationCapability() {
        assertThat(processor.getImplementedCapabilities(new FakeImapSession())).containsExactly(ImapConstants.SUPPORTS_ANNOTATION);
    }

    @Test
    public void processShouldResponseNoWithNoSuchMailboxWhenManagerThrowMailboxNotFoundException() throws Exception {
        doThrow(MailboxNotFoundException.class).when(mockMailboxManager).updateAnnotations(eq(inbox),
            eq(mockMailboxSession), eq(mailboxAnnotations));

        processor.process(request, mockResponder, imapSession);

        verify(mockStatusResponseFactory, times(1)).taggedNo(any(Tag.class), any(ImapCommand.class),
                humanTextCaptor.capture(), any(StatusResponse.ResponseCode.class));

        assertThat(humanTextCaptor.getAllValues()).containsOnly(HumanReadableText.FAILURE_NO_SUCH_MAILBOX);
    }

    @Test
    public void processShouldResponseNoWithGenericFailureWhenManagerThrowMailboxException() throws Exception {
        doThrow(MailboxException.class).when(mockMailboxManager).updateAnnotations(eq(inbox), eq(mockMailboxSession), eq(mailboxAnnotations));

        processor.process(request, mockResponder, imapSession);

        verify(mockStatusResponseFactory, times(1)).taggedNo(any(Tag.class), any(ImapCommand.class), humanTextCaptor.capture());

        assertThat(humanTextCaptor.getAllValues()).containsOnly(HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
    }

    @Test
    public void processShouldWorkWithCompleteResponse() throws Exception {
        when(mockStatusResponseFactory.taggedOk(any(Tag.class), any(ImapCommand.class), any(HumanReadableText.class)))
            .thenReturn(okResponse);

        processor.process(request, mockResponder, imapSession);

        verify(mockMailboxManager).updateAnnotations(inbox, mockMailboxSession, mailboxAnnotations);
        verify(mockResponder).respond(okResponse);
        verify(mockStatusResponseFactory, times(1)).taggedOk(any(Tag.class), any(ImapCommand.class), humanTextCaptor.capture());

        assertThat(humanTextCaptor.getAllValues()).containsOnly(HumanReadableText.COMPLETED);
    }

    @Test
    public void processShouldResponseNoWhenManagerThrowsAnnotationException() throws Exception {
        doThrow(AnnotationException.class).when(mockMailboxManager).updateAnnotations(eq(inbox), eq(mockMailboxSession), eq(mailboxAnnotations));

        processor.process(request, mockResponder, imapSession);

        verify(mockStatusResponseFactory, times(1)).taggedNo(any(Tag.class), any(ImapCommand.class), humanTextCaptor.capture());

        assertThat(humanTextCaptor.getAllValues().get(FIRST_ELEMENT_INDEX).getKey()).isEqualTo(HumanReadableText.MAILBOX_ANNOTATION_KEY);

    }
}