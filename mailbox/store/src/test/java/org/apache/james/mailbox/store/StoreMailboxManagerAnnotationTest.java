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

package org.apache.james.mailbox.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class StoreMailboxManagerAnnotationTest {
    static final MailboxAnnotationKey PRIVATE_KEY = new MailboxAnnotationKey("/private/comment");
    static final MailboxAnnotationKey SHARED_KEY = new MailboxAnnotationKey("/shared/comment");

    static final MailboxAnnotation PRIVATE_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_KEY, "My comment");
    static final MailboxAnnotation SHARED_ANNOTATION =  MailboxAnnotation.newInstance(SHARED_KEY, "My shared comment");
    static final Set<MailboxAnnotationKey> KEYS = ImmutableSet.of(PRIVATE_KEY);

    static final List<MailboxAnnotation> ANNOTATIONS = ImmutableList.of(PRIVATE_ANNOTATION, SHARED_ANNOTATION);
    static final List<MailboxAnnotation> ANNOTATIONS_WITH_NIL_ENTRY = ImmutableList.of(PRIVATE_ANNOTATION, MailboxAnnotation.nil(SHARED_KEY));

    @Mock MailboxSessionMapperFactory mailboxSessionMapperFactory;
    @Mock StoreRightManager storeRightManager;
    @Mock MailboxMapper mailboxMapper;
    @Mock AnnotationMapper annotationMapper;
    @Mock MailboxPath mailboxPath;
    @Mock Mailbox mailbox;
    @Mock MailboxId mailboxId;
    MailboxSession session;

    StoreMailboxAnnotationManager annotationManager;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        session = MailboxSessionUtil.create(Username.of("userName"));

        when(mailboxSessionMapperFactory.getMailboxMapper(eq(session))).thenReturn(mailboxMapper);
        when(mailboxSessionMapperFactory.getAnnotationMapper(eq(session))).thenReturn(annotationMapper);
        when(mailbox.getMailboxId()).thenReturn(mailboxId);
        when(mailboxMapper.findMailboxByPath(eq(mailboxPath))).thenReturn(Mono.just(mailbox));
        when(annotationMapper.execute(any(Mapper.Transaction.class)))
            .thenAnswer(invocationOnMock -> {
                Mapper.Transaction<?> transaction = (Mapper.Transaction<?>) invocationOnMock.getArguments()[0];
                return transaction.run();
            });
        when(annotationMapper.executeReactive(any(Mono.class)))
            .thenAnswer(invocationOnMock -> invocationOnMock.getArguments()[0]);

        when(storeRightManager.hasRight(any(Mailbox.class), any(MailboxACL.Right.class), any(MailboxSession.class)))
            .thenReturn(true);
        when(storeRightManager.hasRightReactive(any(MailboxPath.class), any(MailboxACL.Right.class), any(MailboxSession.class)))
            .thenReturn(Mono.just(true));

        annotationManager = spy(new StoreMailboxAnnotationManager(mailboxSessionMapperFactory,
            storeRightManager));
    }

    @Test
    void updateAnnotationsShouldThrowExceptionWhenDoesNotLookupMailbox() {
        when(mailboxMapper.findMailboxByPath(eq(mailboxPath))).thenReturn(Mono.error(new MailboxException()));

        assertThatThrownBy(() -> annotationManager.updateAnnotations(mailboxPath, session, ImmutableList.of(PRIVATE_ANNOTATION)))
            .isInstanceOf(MailboxException.class);
    }

    @Test
    void updateAnnotationsShouldCallAnnotationMapperToInsertAnnotation() throws MailboxException {
        when(annotationMapper.existReactive(eq(mailbox.getMailboxId()), any())).thenReturn(Mono.just(true));
        when(annotationMapper.insertAnnotationReactive(eq(mailbox.getMailboxId()), any())).thenReturn(Mono.empty());
        annotationManager.updateAnnotations(mailboxPath, session, ANNOTATIONS);

        verify(annotationMapper, times(2)).insertAnnotationReactive(eq(mailboxId), any(MailboxAnnotation.class));
    }

    @Test
    void updateAnnotationsShouldCallAnnotationMapperToDeleteAnnotation() throws MailboxException {
        when(annotationMapper.existReactive(eq(mailbox.getMailboxId()), any())).thenReturn(Mono.just(true));
        when(annotationMapper.insertAnnotationReactive(eq(mailbox.getMailboxId()), any())).thenReturn(Mono.empty());
        when(annotationMapper.deleteAnnotationReactive(eq(mailbox.getMailboxId()), any())).thenReturn(Mono.empty());
        annotationManager.updateAnnotations(mailboxPath, session, ANNOTATIONS_WITH_NIL_ENTRY);

        verify(annotationMapper, times(1)).insertAnnotationReactive(eq(mailboxId), eq(PRIVATE_ANNOTATION));
        verify(annotationMapper, times(1)).deleteAnnotationReactive(eq(mailboxId), eq(SHARED_KEY));
    }

    @Test
    void getAllAnnotationsShouldThrowExceptionWhenDoesNotLookupMailbox() throws Exception {
        when(mailboxMapper.findMailboxByPath(eq(mailboxPath))).thenReturn(Mono.error(new MailboxException()));

        assertThatThrownBy(() -> annotationManager.getAllAnnotations(mailboxPath, session))
            .isInstanceOf(MailboxException.class);
    }

    @Test
    void getAllAnnotationsShouldReturnEmptyForNonStoredAnnotation() throws Exception {
        when(mailboxMapper.findMailboxByPath(eq(mailboxPath))).thenReturn(Mono.just(mailbox));
        when(annotationMapper.getAllAnnotationsReactive(eq(mailboxId))).thenReturn(Flux.fromIterable(List.of()));

        assertThat(annotationManager.getAllAnnotations(mailboxPath, session)).isEmpty();
    }

    @Test
    void getAllAnnotationsShouldReturnStoredAnnotation() throws Exception {
        when(mailboxMapper.findMailboxByPath(eq(mailboxPath))).thenReturn(Mono.just(mailbox));
        when(annotationMapper.getAllAnnotationsReactive(eq(mailboxId))).thenReturn(Flux.fromIterable(ANNOTATIONS));

        assertThat(annotationManager.getAllAnnotations(mailboxPath, session)).isEqualTo(ANNOTATIONS);
    }

    @Test
    void getAnnotationsByKeysShouldThrowExceptionWhenDoesNotLookupMailbox() {
        when(mailboxMapper.findMailboxByPath(eq(mailboxPath))).thenReturn(Mono.error(new MailboxException()));

        assertThatThrownBy(() -> annotationManager.getAnnotationsByKeys(mailboxPath, session, KEYS))
            .isInstanceOf(MailboxException.class);
    }

    @Test
    void getAnnotationsByKeysShouldRetrieveStoreAnnotationsByKey() throws MailboxException {
        when(mailboxMapper.findMailboxByPath(eq(mailboxPath))).thenReturn(Mono.just(mailbox));
        when(annotationMapper.getAnnotationsByKeysReactive(eq(mailboxId), eq(KEYS))).thenReturn(Flux.fromIterable(ANNOTATIONS));

        assertThat(annotationManager.getAnnotationsByKeys(mailboxPath, session, KEYS)).isEqualTo(ANNOTATIONS);
    }
}
