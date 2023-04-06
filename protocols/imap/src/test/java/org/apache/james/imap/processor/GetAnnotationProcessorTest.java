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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.GetMetadataRequest;
import org.apache.james.imap.message.request.GetMetadataRequest.Depth;
import org.apache.james.imap.message.response.MetadataResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class GetAnnotationProcessorTest {
    private static final int FIRST_ELEMENT_INDEX = 0;

    private static final MailboxAnnotationKey PRIVATE_KEY = new MailboxAnnotationKey("/private/comment");
    private static final MailboxAnnotationKey PRIVATE_CHILD_KEY = new MailboxAnnotationKey("/private/comment/user");
    private static final MailboxAnnotationKey PRIVATE_GRANDCHILD_KEY = new MailboxAnnotationKey("/private/comment/user/name");
    private static final MailboxAnnotationKey SHARED_KEY = new MailboxAnnotationKey("/shared/comment");

    private static final MailboxAnnotation SHARED_ANNOTATION = MailboxAnnotation.newInstance(SHARED_KEY, "The shared size");
    private static final MailboxAnnotation PRIVATE_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_KEY, "The short size");
    private static final MailboxAnnotation PRIVATE_CHILD_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_CHILD_KEY, "The middle size");
    private static final MailboxAnnotation PRIVATE_GRANDCHILD_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_GRANDCHILD_KEY, "The longest value size");

    private GetMetadataProcessor processor;
    private MailboxManager mockMailboxManager;
    private StatusResponseFactory mockStatusResponseFactory;
    private ImapProcessor.Responder mockResponder;
    private FakeImapSession imapSession;
    private MailboxSession mailboxSession;
    private Set<MailboxAnnotationKey> keys;
    private StatusResponse statusResponse;
    private GetMetadataRequest.Builder annotationRequestBuilder;
    private MailboxPath inbox;
    private ArgumentCaptor<HumanReadableText> humanTextCaptor;
    private ArgumentCaptor<ResponseCode> captorResponsecode;
    private ArgumentCaptor<MetadataResponse> captorAnnotationResponse;

    private void initAndMockData() {
        statusResponse = mock(StatusResponse.class);
        mockMailboxManager = mock(MailboxManager.class);
        when(mockMailboxManager.getSupportedMailboxCapabilities()).thenReturn(EnumSet.of(MailboxManager.MailboxCapabilities.Annotation));
        when(mockMailboxManager.manageProcessing(any(), any())).thenAnswer((Answer<Mono>) invocation -> {
            Object[] args = invocation.getArguments();
            return (Mono) args[0];
        });
        mockStatusResponseFactory = mock(StatusResponseFactory.class);
        mockResponder = mock(ImapProcessor.Responder.class);
        imapSession = new FakeImapSession();

        Username username = Username.of("username");
        mailboxSession = MailboxSessionUtil.create(username);
        inbox = MailboxPath.inbox(username);
        keys = ImmutableSet.of(PRIVATE_KEY);
        annotationRequestBuilder = GetMetadataRequest.builder()
            .tag(TAG)
            .mailboxName(ImapConstants.INBOX_NAME);
        humanTextCaptor = ArgumentCaptor.forClass(HumanReadableText.class);
        captorResponsecode = ArgumentCaptor.forClass(ResponseCode.class);
        captorAnnotationResponse = ArgumentCaptor.forClass(MetadataResponse.class);

        imapSession.authenticated();
        imapSession.setMailboxSession(mailboxSession);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        initAndMockData();

        processor = new GetMetadataProcessor(mockMailboxManager, mockStatusResponseFactory, new RecordingMetricFactory());
    }

    @Test
    void getImplementedCapabilitiesShouldContainSupportAnnotationWhenSupportedByMailboxManager() {
        assertThat(processor.getImplementedCapabilities(new FakeImapSession())).containsExactly(ImapConstants.SUPPORTS_ANNOTATION);
    }

    @Test
    void processShouldResponseNoWithFailureWhenMailboxDoesNotExist() throws Exception {

        when(mockMailboxManager.getAllAnnotationsReactive(inbox, mailboxSession))
            .thenReturn(Flux.error(new MailboxNotFoundException("")));


        when(mockStatusResponseFactory.taggedNo(any(Tag.class), any(ImapCommand.class), any(HumanReadableText.class), any(ResponseCode.class)))
            .thenReturn(statusResponse);

        processor.process(annotationRequestBuilder.build(), mockResponder, imapSession);

        verify(mockStatusResponseFactory, times(1)).taggedNo(any(Tag.class), any(ImapCommand.class), humanTextCaptor.capture(), captorResponsecode.capture());
        verify(mockResponder).respond(statusResponse);
        verifyNoMoreInteractions(mockResponder);

        assertThat(humanTextCaptor.getAllValues()).containsOnly(HumanReadableText.FAILURE_NO_SUCH_MAILBOX);
        assertThat(captorResponsecode.getAllValues()).containsOnly(ResponseCode.tryCreate());
    }

    @Test
    void processShouldResponseNoWithGenericFailureWhenManagerThrowMailboxException() throws Exception {
        when(mockMailboxManager.getAllAnnotationsReactive(inbox, mailboxSession))
            .thenReturn(Flux.error(new MailboxException("")));

        when(mockStatusResponseFactory.taggedNo(any(Tag.class), any(ImapCommand.class), any(HumanReadableText.class)))
            .thenReturn(statusResponse);

        processor.process(annotationRequestBuilder.build(), mockResponder, imapSession);

        verify(mockStatusResponseFactory, times(1)).taggedNo(any(Tag.class), any(ImapCommand.class), humanTextCaptor.capture());
        verify(mockResponder).respond(statusResponse);
        verifyNoMoreInteractions(mockResponder);

        assertThat(humanTextCaptor.getAllValues()).containsOnly(HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
    }

    @Test
    void processShouldGetAllAnnotationsAndReturnCompleteResponse() {
        when(mockMailboxManager.getAllAnnotationsReactive(inbox, mailboxSession))
            .thenReturn(Flux.empty());
        processor.process(annotationRequestBuilder.build(), mockResponder, imapSession);

        verify(mockMailboxManager, times(1)).getAllAnnotationsReactive(inbox, mailboxSession);
        verify(mockStatusResponseFactory, times(1)).taggedOk(any(Tag.class), any(ImapCommand.class), humanTextCaptor.capture());
        verify(mockResponder, times(2)).respond(captorAnnotationResponse.capture());

        verifyNoMoreInteractions(mockResponder);

        assertThat(humanTextCaptor.getAllValues()).containsOnly(HumanReadableText.COMPLETED);
    }

    @Test
    void processShouldGetAnnotationsByKeysAndReturnCompleteResponse() {
        when(mockMailboxManager.getAnnotationsByKeysReactive(inbox, mailboxSession, keys))
            .thenReturn(Flux.empty());
        processor.process(annotationRequestBuilder.keys(keys).build(), mockResponder, imapSession);

        verify(mockMailboxManager, times(1)).getAnnotationsByKeysReactive(eq(inbox), eq(mailboxSession), eq(keys));
        verify(mockStatusResponseFactory, times(1)).taggedOk(any(Tag.class), any(ImapCommand.class), humanTextCaptor.capture());
        verify(mockResponder, times(2)).respond(captorAnnotationResponse.capture());
        verifyNoMoreInteractions(mockResponder);

        assertThat(humanTextCaptor.getAllValues()).containsOnly(HumanReadableText.COMPLETED);
    }

    @Test
    void processShouldGetAnnotationsAndReturnCompleteResponseWithTheLongestEntryInfoWhenLimitMaxsize() {
        when(mockMailboxManager.getAllAnnotationsReactive(inbox, mailboxSession))
            .thenReturn(Flux.fromIterable(ImmutableList.of(PRIVATE_ANNOTATION, SHARED_ANNOTATION, PRIVATE_GRANDCHILD_ANNOTATION)));

        processor.process(annotationRequestBuilder.maxsize(Optional.of(10)).build(), mockResponder, imapSession);

        verify(mockStatusResponseFactory, times(1)).taggedOk(any(Tag.class),
            any(ImapCommand.class),
            humanTextCaptor.capture(),
            captorResponsecode.capture());
        verify(mockResponder, times(2)).respond(captorAnnotationResponse.capture());
        verifyNoMoreInteractions(mockResponder);

        assertThat(humanTextCaptor.getAllValues()).containsOnly(HumanReadableText.COMPLETED);
        assertThat(captorResponsecode.getAllValues()).containsOnly(ResponseCode.longestMetadataEntry(22));
    }

    @Test
    void processShouldGetAnnotationsAndReturnCompleteResponseDoesNotTruncateDataByMaxsize() {
        when(mockMailboxManager.getAllAnnotationsReactive(inbox, mailboxSession))
            .thenReturn(Flux.fromIterable(ImmutableList.of(PRIVATE_ANNOTATION, SHARED_ANNOTATION)));

        processor.process(annotationRequestBuilder.maxsize(Optional.of(100)).build(), mockResponder, imapSession);

        verify(mockStatusResponseFactory, times(1)).taggedOk(any(Tag.class),
            any(ImapCommand.class),
            humanTextCaptor.capture());
        verify(mockResponder, times(2)).respond(captorAnnotationResponse.capture());
        verifyNoMoreInteractions(mockResponder);

        assertThat(humanTextCaptor.getAllValues()).containsOnly(HumanReadableText.COMPLETED);
    }

    @Test
    void processShouldGetAnnotationsAndReturnCompleteResponseWithTruncateDataByMaxsize() {
        when(mockMailboxManager.getAllAnnotationsReactive(inbox, mailboxSession))
            .thenReturn(Flux.fromIterable(ImmutableList.of(SHARED_ANNOTATION, PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION, PRIVATE_GRANDCHILD_ANNOTATION)));

        processor.process(annotationRequestBuilder.maxsize(Optional.of(15)).build(), mockResponder, imapSession);

        verify(mockStatusResponseFactory, times(1)).taggedOk(any(Tag.class),
            any(ImapCommand.class),
            humanTextCaptor.capture(),
            any(ResponseCode.class));
        verify(mockResponder, times(2)).respond(captorAnnotationResponse.capture());
        verifyNoMoreInteractions(mockResponder);

        MetadataResponse resultAnnotation = captorAnnotationResponse.getAllValues().get(FIRST_ELEMENT_INDEX);
        assertThat(resultAnnotation.getMailboxAnnotations()).contains(PRIVATE_ANNOTATION, SHARED_ANNOTATION);
    }

    @Test
    void processShouldGetAnnotationsAndReturnCompleteResponseDoesnotTruncateDataByMaxsizeWhenNoMoreOverSizeItem() {
        when(mockMailboxManager.getAllAnnotationsReactive(inbox, mailboxSession))
            .thenReturn(Flux.fromIterable(ImmutableList.of(SHARED_ANNOTATION, PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION, PRIVATE_GRANDCHILD_ANNOTATION)));

        processor.process(annotationRequestBuilder.maxsize(Optional.of(100)).build(), mockResponder, imapSession);

        verify(mockStatusResponseFactory, times(1)).taggedOk(any(Tag.class),
            any(ImapCommand.class),
            humanTextCaptor.capture());
        verify(mockResponder, times(2)).respond(captorAnnotationResponse.capture());
        verifyNoMoreInteractions(mockResponder);

        MetadataResponse resultAnnotation = captorAnnotationResponse.getAllValues().get(FIRST_ELEMENT_INDEX);
        assertThat(resultAnnotation.getMailboxAnnotations()).contains(PRIVATE_ANNOTATION, SHARED_ANNOTATION, PRIVATE_CHILD_ANNOTATION, PRIVATE_GRANDCHILD_ANNOTATION);
    }

    @Test
    void processShouldGetAnnotationsByOneDepthAndReturnCompleteResponseWithTruncateDataByMaxsize() {
        when(mockMailboxManager.getAnnotationsByKeysWithOneDepthReactive(inbox, mailboxSession, keys))
            .thenReturn(Flux.fromIterable(ImmutableList.of(PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION)));

        processor.process(annotationRequestBuilder.maxsize(Optional.of(14)).depth(Depth.ONE).keys(keys).build(), mockResponder, imapSession);

        verify(mockMailboxManager, times(1)).getAnnotationsByKeysWithOneDepthReactive(eq(inbox), eq(mailboxSession), eq(keys));
        verify(mockStatusResponseFactory, times(1)).taggedOk(any(Tag.class),
            any(ImapCommand.class),
            humanTextCaptor.capture(),
            any(ResponseCode.class));
        verify(mockResponder, times(2)).respond(captorAnnotationResponse.capture());
        verifyNoMoreInteractions(mockResponder);

        MetadataResponse resultAnnotation = captorAnnotationResponse.getAllValues().get(FIRST_ELEMENT_INDEX);
        assertThat(resultAnnotation.getMailboxAnnotations()).contains(PRIVATE_ANNOTATION);
    }

    @Test
    void processShouldGetAnnotationsAndReturnCompleteResponseWithTruncateDataByLessThenOrEqualMaxsize() throws Exception {
        when(mockMailboxManager.getAllAnnotationsReactive(inbox, mailboxSession))
            .thenReturn(Flux.fromIterable(ImmutableList.of(PRIVATE_ANNOTATION, SHARED_ANNOTATION)));

        processor.process(annotationRequestBuilder.maxsize(Optional.of(15)).build(), mockResponder, imapSession);

        verify(mockMailboxManager, times(1)).getAllAnnotationsReactive(eq(inbox), eq(mailboxSession));
        verify(mockStatusResponseFactory, times(1)).taggedOk(any(Tag.class),
            any(ImapCommand.class),
            humanTextCaptor.capture());

        verify(mockResponder, times(2)).respond(captorAnnotationResponse.capture());
        verifyNoMoreInteractions(mockResponder);

        MetadataResponse resultAnnotation = captorAnnotationResponse.getAllValues().get(FIRST_ELEMENT_INDEX);
        assertThat(resultAnnotation.getMailboxAnnotations()).contains(PRIVATE_ANNOTATION, SHARED_ANNOTATION);
    }

    @Test
    void processShouldGetAnnotationsByInfinityDepthAndReturnCompleteResponseWithTruncateDataByMaxsize() throws Exception {
        when(mockMailboxManager.getAnnotationsByKeysWithAllDepthReactive(inbox, mailboxSession, keys))
            .thenReturn(Flux.fromIterable(ImmutableList.of(PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION, PRIVATE_GRANDCHILD_ANNOTATION)));

        processor.process(annotationRequestBuilder.maxsize(Optional.of(14)).depth(Depth.INFINITY).keys(keys).build(), mockResponder, imapSession);

        verify(mockMailboxManager, times(1)).getAnnotationsByKeysWithAllDepthReactive(eq(inbox), eq(mailboxSession), eq(keys));
        verify(mockStatusResponseFactory, times(1)).taggedOk(any(Tag.class),
            any(ImapCommand.class),
            humanTextCaptor.capture(),
            any(ResponseCode.class));
        verify(mockResponder, times(2)).respond(captorAnnotationResponse.capture());
        verifyNoMoreInteractions(mockResponder);

        MetadataResponse resultAnnotation = captorAnnotationResponse.getAllValues().get(FIRST_ELEMENT_INDEX);

        assertThat(resultAnnotation.getMailboxAnnotations()).contains(PRIVATE_ANNOTATION);
    }

    @Test
    void processShouldGetAnnotationsByInfinityDepthAndReturnCompleteResponse() throws Exception {
        when(mockMailboxManager.getAnnotationsByKeysWithAllDepthReactive(inbox, mailboxSession, keys))
            .thenReturn(Flux.fromIterable(ImmutableList.of(PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION, PRIVATE_GRANDCHILD_ANNOTATION)));

        processor.process(annotationRequestBuilder.depth(Depth.INFINITY).keys(keys).build(), mockResponder, imapSession);

        verify(mockStatusResponseFactory, times(1)).taggedOk(any(Tag.class),
            any(ImapCommand.class),
            humanTextCaptor.capture());
        verify(mockResponder, times(2)).respond(captorAnnotationResponse.capture());
        verifyNoMoreInteractions(mockResponder);

        MetadataResponse resultAnnotation = captorAnnotationResponse.getAllValues().get(FIRST_ELEMENT_INDEX);
        assertThat(resultAnnotation.getMailboxAnnotations()).contains(PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION, PRIVATE_GRANDCHILD_ANNOTATION);
    }


}