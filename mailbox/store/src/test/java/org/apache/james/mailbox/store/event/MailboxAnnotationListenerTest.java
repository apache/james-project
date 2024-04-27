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
package org.apache.james.mailbox.store.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.events.Event;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.MailboxEvents.MailboxAdded;
import org.apache.james.mailbox.events.MailboxEvents.MailboxEvent;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class MailboxAnnotationListenerTest {
    static final Username USER = Username.of("user");
    static final MailboxPath MAILBOX_PATH = new MailboxPath("namespace", USER, "name");

    static final MailboxAnnotationKey PRIVATE_KEY = new MailboxAnnotationKey("/private/comment");
    static final MailboxAnnotationKey SHARED_KEY = new MailboxAnnotationKey("/shared/comment");

    static final MailboxAnnotation PRIVATE_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_KEY, "My private comment");
    static final MailboxAnnotation SHARED_ANNOTATION =  MailboxAnnotation.newInstance(SHARED_KEY, "My shared comment");

    static final List<MailboxAnnotation> ANNOTATIONS = ImmutableList.of(PRIVATE_ANNOTATION, SHARED_ANNOTATION);
    static final TestId MAILBOX_ID = TestId.of(45);

    @Mock SessionProvider sessionProvider;
    @Mock MailboxSessionMapperFactory mailboxSessionMapperFactory;
    @Mock AnnotationMapper annotationMapper;
    @Mock MailboxId mailboxId;

    MailboxAnnotationListener listener;
    MailboxEvent deleteEvent;
    MailboxSession mailboxSession;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mailboxSession = MailboxSessionUtil.create(Username.of("test"));
        listener = new MailboxAnnotationListener(mailboxSessionMapperFactory, sessionProvider);

        deleteEvent = EventFactory.mailboxDeleted()
            .randomEventId()
            .mailboxSession(mailboxSession)
            .mailboxId(mailboxId)
            .mailboxPath(MailboxPath.forUser(USER, "name"))
            .quotaRoot(QuotaRoot.quotaRoot("root", Optional.empty()))
            .mailboxACL(new MailboxACL())
            .quotaCount(QuotaCountUsage.count(123))
            .quotaSize(QuotaSizeUsage.size(456))
            .build();

        when(sessionProvider.createSystemSession(deleteEvent.getUsername()))
            .thenReturn(mailboxSession);
        when(mailboxSessionMapperFactory.getAnnotationMapper(eq(mailboxSession))).thenReturn(annotationMapper);
    }

    @Test
    void deserializeMailboxAnnotationListenerGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.mailbox.store.event.MailboxAnnotationListener$MailboxAnnotationListenerGroup"))
            .isEqualTo(new MailboxAnnotationListener.MailboxAnnotationListenerGroup());
    }

    @Test
    void eventShouldDoNothingIfDoNotHaveMailboxDeletionEvent() throws Exception {
        MailboxEvent event = new MailboxAdded(null, null, MAILBOX_PATH, MAILBOX_ID, Event.EventId.random());
        listener.event(event);

        verifyNoMoreInteractions(mailboxSessionMapperFactory);
        verifyNoMoreInteractions(annotationMapper);
    }

    @Test
    void eventShouldDoNothingIfMailboxDoesNotHaveAnyAnnotation() throws Exception {
        when(annotationMapper.getAllAnnotationsReactive(any(MailboxId.class))).thenReturn(Flux.fromIterable(List.of()));

        listener.event(deleteEvent);

        verify(mailboxSessionMapperFactory).getAnnotationMapper(eq(mailboxSession));
        verify(mailboxSessionMapperFactory).endProcessingRequest(eq(mailboxSession));
        verify(annotationMapper).getAllAnnotationsReactive(eq(mailboxId));

        verifyNoMoreInteractions(mailboxSessionMapperFactory);
        verifyNoMoreInteractions(annotationMapper);

    }

    @Test
    void eventShoudlDeleteAllMailboxAnnotation() throws Exception {
        when(annotationMapper.getAllAnnotationsReactive(eq(mailboxId))).thenReturn(Flux.fromIterable(ANNOTATIONS));
        when(annotationMapper.deleteAnnotationReactive(eq(mailboxId), any())).thenReturn(Mono.empty());

        listener.event(deleteEvent);

        verify(mailboxSessionMapperFactory).getAnnotationMapper(eq(mailboxSession));
        verify(mailboxSessionMapperFactory).endProcessingRequest(eq(mailboxSession));
        verify(annotationMapper).getAllAnnotationsReactive(eq(mailboxId));
        verify(annotationMapper).deleteAnnotationReactive(eq(mailboxId), eq(PRIVATE_KEY));
        verify(annotationMapper).deleteAnnotationReactive(eq(mailboxId), eq(SHARED_KEY));

        verifyNoMoreInteractions(mailboxSessionMapperFactory);
        verifyNoMoreInteractions(annotationMapper);

    }

    @Test
    void eventShouldPropagateFailure() {
        when(annotationMapper.getAllAnnotationsReactive(eq(mailboxId))).thenReturn(Flux.fromIterable(ANNOTATIONS));
        when(annotationMapper.deleteAnnotationReactive(eq(mailboxId), eq(PRIVATE_KEY)))
            .thenReturn(Mono.error(new RuntimeException()));

        assertThatThrownBy(() -> listener.event(deleteEvent)).isInstanceOf(RuntimeException.class);

        verify(mailboxSessionMapperFactory).getAnnotationMapper(eq(mailboxSession));
        verify(mailboxSessionMapperFactory).endProcessingRequest(eq(mailboxSession));
        verify(annotationMapper).getAllAnnotationsReactive(eq(mailboxId));
        verify(annotationMapper).deleteAnnotationReactive(eq(mailboxId), eq(PRIVATE_KEY));

        verifyNoMoreInteractions(mailboxSessionMapperFactory);
        verifyNoMoreInteractions(annotationMapper);
    }
}