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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.SessionProvider;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.ImmutableList;

public class MailboxAnnotationListenerTest {
    private static final MailboxPath MAILBOX_PATH = new MailboxPath("namespace", "user", "name");

    private static final MailboxAnnotationKey PRIVATE_KEY = new MailboxAnnotationKey("/private/comment");
    private static final MailboxAnnotationKey SHARED_KEY = new MailboxAnnotationKey("/shared/comment");

    private static final MailboxAnnotation PRIVATE_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_KEY, "My private comment");
    private static final MailboxAnnotation SHARED_ANNOTATION =  MailboxAnnotation.newInstance(SHARED_KEY, "My shared comment");

    private static final List<MailboxAnnotation> ANNOTATIONS = ImmutableList.of(PRIVATE_ANNOTATION, SHARED_ANNOTATION);
    public static final int UID_VALIDITY = 145;
    public static final TestId MAILBOX_ID = TestId.of(45);

    @Mock private SessionProvider sessionProvider;
    @Mock private MailboxSessionMapperFactory mailboxSessionMapperFactory;
    @Mock private AnnotationMapper annotationMapper;
    @Mock private MailboxId mailboxId;

    private MailboxAnnotationListener listener;
    private MailboxListener.MailboxEvent deleteEvent;
    private MailboxSession mailboxSession;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mailboxSession = MailboxSessionUtil.create("test");
        listener = new MailboxAnnotationListener(mailboxSessionMapperFactory, sessionProvider);

        deleteEvent = EventFactory.mailboxDeleted()
            .randomEventId()
            .mailboxSession(mailboxSession)
            .mailboxId(mailboxId)
            .mailboxPath(MailboxPath.forUser("user", "name"))
            .quotaRoot(QuotaRoot.quotaRoot("root", Optional.empty()))
            .quotaCount(QuotaCount.count(123))
            .quotaSize(QuotaSize.size(456))
            .build();

        when(sessionProvider.createSystemSession(deleteEvent.getUser().asString()))
            .thenReturn(mailboxSession);
        when(mailboxSessionMapperFactory.getAnnotationMapper(eq(mailboxSession))).thenReturn(annotationMapper);
    }

    @Test
    public void eventShouldDoNothingIfDoNotHaveMailboxDeletionEvent() {
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxAdded(null, null, MAILBOX_PATH, MAILBOX_ID, Event.EventId.random());
        listener.event(event);

        verifyNoMoreInteractions(mailboxSessionMapperFactory);
        verifyNoMoreInteractions(annotationMapper);
    }

    @Test
    public void eventShoudlDoNothingIfMailboxDoesNotHaveAnyAnnotation() throws Exception {
        when(annotationMapper.getAllAnnotations(any(MailboxId.class))).thenReturn(ImmutableList.<MailboxAnnotation>of());

        listener.event(deleteEvent);

        verify(mailboxSessionMapperFactory).getAnnotationMapper(eq(mailboxSession));
        verify(annotationMapper).getAllAnnotations(eq(mailboxId));

        verifyNoMoreInteractions(mailboxSessionMapperFactory);
        verifyNoMoreInteractions(annotationMapper);

    }

    @Test
    public void eventShoudlDeleteAllMailboxAnnotation() throws Exception {
        when(annotationMapper.getAllAnnotations(eq(mailboxId))).thenReturn(ANNOTATIONS);

        listener.event(deleteEvent);

        verify(mailboxSessionMapperFactory).getAnnotationMapper(eq(mailboxSession));
        verify(annotationMapper).getAllAnnotations(eq(mailboxId));
        verify(annotationMapper).deleteAnnotation(eq(mailboxId), eq(PRIVATE_KEY));
        verify(annotationMapper).deleteAnnotation(eq(mailboxId), eq(SHARED_KEY));

        verifyNoMoreInteractions(mailboxSessionMapperFactory);
        verifyNoMoreInteractions(annotationMapper);

    }

    @Test
    public void eventShouldDeteleAllMailboxIfHasAnyOneFailed() throws Exception {
        when(annotationMapper.getAllAnnotations((eq(mailboxId)))).thenReturn(ANNOTATIONS);
        doThrow(new RuntimeException()).when(annotationMapper).deleteAnnotation(eq(mailboxId), eq(PRIVATE_KEY));

        listener.event(deleteEvent);

        verify(mailboxSessionMapperFactory).getAnnotationMapper(eq(mailboxSession));
        verify(annotationMapper).getAllAnnotations(eq(mailboxId));
        verify(annotationMapper).deleteAnnotation(eq(mailboxId), eq(PRIVATE_KEY));
        verify(annotationMapper).deleteAnnotation(eq(mailboxId), eq(SHARED_KEY));

        verifyNoMoreInteractions(mailboxSessionMapperFactory);
        verifyNoMoreInteractions(annotationMapper);
    }
}