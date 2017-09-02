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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class StoreMailboxManagerAnnotationTest {
    private static final MailboxAnnotationKey PRIVATE_KEY = new MailboxAnnotationKey("/private/comment");
    private static final MailboxAnnotationKey SHARED_KEY = new MailboxAnnotationKey("/shared/comment");

    private static final MailboxAnnotation PRIVATE_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_KEY, "My private comment");
    private static final MailboxAnnotation SHARED_ANNOTATION =  MailboxAnnotation.newInstance(SHARED_KEY, "My shared comment");
    private static final Set<MailboxAnnotationKey> KEYS = ImmutableSet.of(PRIVATE_KEY);

    private static final List<MailboxAnnotation> ANNOTATIONS = ImmutableList.of(PRIVATE_ANNOTATION, SHARED_ANNOTATION);
    private static final List<MailboxAnnotation> ANNOTATIONS_WITH_NIL_ENTRY = ImmutableList.of(PRIVATE_ANNOTATION, MailboxAnnotation.nil(SHARED_KEY));

    @Mock private MailboxSessionMapperFactory mailboxSessionMapperFactory;
    @Mock private Authenticator authenticator;
    @Mock private Authorizator authorizator;
    @Mock private MailboxACLResolver aclResolver;
    @Mock private GroupMembershipResolver groupMembershipResolver;
    @Mock private MailboxMapper mailboxMapper;
    @Mock private AnnotationMapper annotationMapper;
    @Mock private MailboxPath mailboxPath;
    @Mock private Mailbox mailbox;
    @Mock private MessageParser messageParser;
    @Mock private MailboxId mailboxId;
    @Mock private MessageId.Factory messageIdFactory;
    private MockMailboxSession session;

    private StoreMailboxManager storeMailboxManager;


    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        session = new MockMailboxSession("userName");

        when(mailboxSessionMapperFactory.getMailboxMapper(eq(session))).thenReturn(mailboxMapper);
        when(mailboxSessionMapperFactory.getAnnotationMapper(eq(session))).thenReturn(annotationMapper);
        when(mailbox.getMailboxId()).thenReturn(mailboxId);
        when(mailboxMapper.findMailboxByPath(eq(mailboxPath))).thenReturn(mailbox);
        when(annotationMapper.execute(any(Mapper.Transaction.class)))
            .thenAnswer(invocationOnMock -> {
                Mapper.Transaction<?> transaction = (Mapper.Transaction<?>) invocationOnMock.getArguments()[0];
                return transaction.run();
            });

        storeMailboxManager = spy(new StoreMailboxManager(mailboxSessionMapperFactory, authenticator, authorizator, aclResolver, groupMembershipResolver, 
                messageParser, messageIdFactory, MailboxConstants.DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX, MailboxConstants.DEFAULT_LIMIT_ANNOTATION_SIZE));
        storeMailboxManager.init();
    }

    @Test(expected = MailboxException.class)
    public void updateAnnotationsShouldThrowExceptionWhenDoesNotLookupMailbox() throws Exception {
        doThrow(MailboxException.class).when(mailboxMapper).findMailboxByPath(eq(mailboxPath));
        storeMailboxManager.updateAnnotations(mailboxPath, session, ImmutableList.of(PRIVATE_ANNOTATION));
    }

    @Test
    public void updateAnnotationsShouldCallAnnotationMapperToInsertAnnotation() throws Exception {
        when(mailboxMapper.findMailboxByPath(eq(mailboxPath))).thenReturn(mailbox);
        storeMailboxManager.updateAnnotations(mailboxPath, session, ANNOTATIONS);

        verify(annotationMapper, times(2)).insertAnnotation(eq(mailboxId), any(MailboxAnnotation.class));
    }

    @Test
    public void updateAnnotationsShouldCallAnnotationMapperToDeleteAnnotation() throws Exception {
        when(mailboxMapper.findMailboxByPath(eq(mailboxPath))).thenReturn(mailbox);
        storeMailboxManager.updateAnnotations(mailboxPath, session, ANNOTATIONS_WITH_NIL_ENTRY);

        verify(annotationMapper, times(1)).insertAnnotation(eq(mailboxId), eq(PRIVATE_ANNOTATION));
        verify(annotationMapper, times(1)).deleteAnnotation(eq(mailboxId), eq(SHARED_KEY));
    }

    @Test(expected = MailboxException.class)
    public void getAllAnnotationsShouldThrowExceptionWhenDoesNotLookupMailbox() throws Exception {
        doThrow(MailboxException.class).when(mailboxMapper).findMailboxByPath(eq(mailboxPath));
        storeMailboxManager.getAllAnnotations(mailboxPath, session);
    }

    @Test
    public void getAllAnnotationsShouldReturnEmptyForNonStoredAnnotation() throws Exception {
        when(mailboxMapper.findMailboxByPath(eq(mailboxPath))).thenReturn(mailbox);
        when(annotationMapper.getAllAnnotations(eq(mailboxId))).thenReturn(Collections.<MailboxAnnotation> emptyList());

        assertThat(storeMailboxManager.getAllAnnotations(mailboxPath, session)).isEmpty();
    }

    @Test
    public void getAllAnnotationsShouldReturnStoredAnnotation() throws Exception {
        when(mailboxMapper.findMailboxByPath(eq(mailboxPath))).thenReturn(mailbox);
        when(annotationMapper.getAllAnnotations(eq(mailboxId))).thenReturn(ANNOTATIONS);

        assertThat(storeMailboxManager.getAllAnnotations(mailboxPath, session)).isEqualTo(ANNOTATIONS);
    }

    @Test(expected = MailboxException.class)
    public void getAnnotationsByKeysShouldThrowExceptionWhenDoesNotLookupMailbox() throws Exception {
        doThrow(MailboxException.class).when(mailboxMapper).findMailboxByPath(eq(mailboxPath));
        storeMailboxManager.getAnnotationsByKeys(mailboxPath, session, KEYS);
    }

    @Test
    public void getAnnotationsByKeysShouldRetrieveStoreAnnotationsByKey() throws Exception {
        when(mailboxMapper.findMailboxByPath(eq(mailboxPath))).thenReturn(mailbox);
        when(annotationMapper.getAnnotationsByKeys(eq(mailboxId), eq(KEYS))).thenReturn(ANNOTATIONS);

        assertThat(storeMailboxManager.getAnnotationsByKeys(mailboxPath, session, KEYS)).isEqualTo(ANNOTATIONS);
    }
}
