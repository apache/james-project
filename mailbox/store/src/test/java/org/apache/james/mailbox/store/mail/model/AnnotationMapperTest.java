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

package org.apache.james.mailbox.store.mail.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableSet;

public abstract class AnnotationMapperTest {
    private static final MailboxAnnotationKey PRIVATE_USER_KEY = new MailboxAnnotationKey("/private/commentuser");
    private static final MailboxAnnotationKey PRIVATE_UPPER_CASE_KEY = new MailboxAnnotationKey("/PRIVATE/COMMENT");
    private static final MailboxAnnotationKey PRIVATE_KEY = new MailboxAnnotationKey("/private/comment");
    private static final MailboxAnnotationKey PRIVATE_CHILD_KEY = new MailboxAnnotationKey("/private/comment/user");
    private static final MailboxAnnotationKey PRIVATE_GRANDCHILD_KEY = new MailboxAnnotationKey("/private/comment/user/name");
    private static final MailboxAnnotationKey SHARED_KEY = new MailboxAnnotationKey("/shared/comment");

    private static final MailboxAnnotation PRIVATE_USER_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_USER_KEY, "My private comment");
    private static final MailboxAnnotation PRIVATE_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_KEY, "My private comment");
    private static final MailboxAnnotation PRIVATE_ANNOTATION_WITH_KEY_UPPER = MailboxAnnotation.newInstance(PRIVATE_UPPER_CASE_KEY, "The annotation with upper key");
    private static final MailboxAnnotation PRIVATE_CHILD_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_CHILD_KEY, "My private comment");
    private static final MailboxAnnotation PRIVATE_ANNOTATION_UPDATE = MailboxAnnotation.newInstance(PRIVATE_KEY, "My updated private comment");
    private static final MailboxAnnotation SHARED_ANNOTATION =  MailboxAnnotation.newInstance(SHARED_KEY, "My shared comment");

    private static final MailboxAnnotation PRIVATE_GRANDCHILD_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_GRANDCHILD_KEY, "My private comment");

    private AnnotationMapper annotationMapper;
    private MailboxId mailboxId;

    @Rule
    public ExpectedException expected = ExpectedException.none();

    protected abstract AnnotationMapper createAnnotationMapper();

    protected abstract MailboxId generateMailboxId();

    public void setUp() throws Exception {
        this.annotationMapper = createAnnotationMapper();
        this.mailboxId = generateMailboxId();
    }

    @Test
    public void insertAnnotationShouldThrowExceptionWithNilData() {
        expected.expect(IllegalArgumentException.class);
        annotationMapper.insertAnnotation(mailboxId, MailboxAnnotation.nil(PRIVATE_KEY));
    }

    @Test
    public void insertAnnotationShouldCreateNewAnnotation() throws MailboxException {
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_ANNOTATION);

        assertThat(annotationMapper.getAllAnnotations(mailboxId)).containsExactly(PRIVATE_ANNOTATION);
    }

    @Test
    public void insertAnnotationShouldUpdateExistedAnnotation() throws MailboxException {
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_ANNOTATION_UPDATE);

        assertThat(annotationMapper.getAllAnnotations(mailboxId)).containsExactly(PRIVATE_ANNOTATION_UPDATE);
    }

    @Test
    public void deleteAnnotationShouldDeleteStoredAnnotation() throws MailboxException {
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_ANNOTATION);
        annotationMapper.deleteAnnotation(mailboxId, PRIVATE_KEY);

        assertThat(annotationMapper.getAllAnnotations(mailboxId)).isEmpty();
    }

    @Test
    public void getEmptyAnnotationsWithNonStoredAnnotations() throws MailboxException {
        assertThat(annotationMapper.getAllAnnotations(mailboxId)).isEmpty();
    }

    @Test
    public void getAllAnnotationsShouldRetrieveStoredAnnotations() throws MailboxException {
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, SHARED_ANNOTATION);

        assertThat(annotationMapper.getAllAnnotations(mailboxId)).containsOnly(PRIVATE_ANNOTATION, SHARED_ANNOTATION);
    }

    @Test
    public void getAnnotationsByKeysShouldReturnStoredAnnotationWithFilter() throws MailboxException {
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_CHILD_ANNOTATION);

        assertThat(annotationMapper.getAnnotationsByKeys(mailboxId, ImmutableSet.of(PRIVATE_KEY)))
            .containsOnly(PRIVATE_ANNOTATION);
    }

    @Test
    public void getAnnotationsByKeysWithOneDepthShouldReturnThatEntryAndItsChildren() throws MailboxException {
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_CHILD_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_GRANDCHILD_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, SHARED_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_USER_ANNOTATION);

        assertThat(annotationMapper.getAnnotationsByKeysWithOneDepth(mailboxId, ImmutableSet.of(PRIVATE_KEY)))
            .containsOnly(PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION);
    }

    @Test
    public void getAnnotationsByKeysWithAllDepthShouldReturnThatEntryAndAllBelowEntries() throws MailboxException {
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_CHILD_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_GRANDCHILD_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, SHARED_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_USER_ANNOTATION);

        assertThat(annotationMapper.getAnnotationsByKeysWithAllDepth(mailboxId, ImmutableSet.of(PRIVATE_KEY)))
            .containsOnly(PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION, PRIVATE_GRANDCHILD_ANNOTATION);
    }

    @Test
    public void getAnnotationsByKeysWithOneDepthShouldReturnTheChildrenEntriesEvenItDoesNotExist() throws Exception {
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_CHILD_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_GRANDCHILD_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, SHARED_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_USER_ANNOTATION);

        assertThat(annotationMapper.getAnnotationsByKeysWithOneDepth(mailboxId, ImmutableSet.of(PRIVATE_KEY)))
            .containsOnly(PRIVATE_CHILD_ANNOTATION);
    }

    @Test
    public void getAnnotationsByKeysWithAllDepthShouldReturnTheChildrenEntriesEvenItDoesNotExist() throws Exception {
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_CHILD_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_GRANDCHILD_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, SHARED_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_USER_ANNOTATION);

        assertThat(annotationMapper.getAnnotationsByKeysWithAllDepth(mailboxId, ImmutableSet.of(PRIVATE_KEY)))
            .containsOnly(PRIVATE_CHILD_ANNOTATION, PRIVATE_GRANDCHILD_ANNOTATION);
    }

    @Test
    public void getAnnotationsByKeysWithOneDepthShouldReturnEmptyWithEmptyInputKeys() throws Exception {
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_CHILD_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_GRANDCHILD_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, SHARED_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_USER_ANNOTATION);

        assertThat(annotationMapper.getAnnotationsByKeysWithOneDepth(mailboxId, ImmutableSet.<MailboxAnnotationKey>of())).isEmpty();
    }

    @Test
    public void getAnnotationsByKeysWithOneDepthShouldReturnEmptyIfDoNotFind() throws Exception {
        annotationMapper.insertAnnotation(mailboxId, SHARED_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_USER_ANNOTATION);

        assertThat(annotationMapper.getAnnotationsByKeysWithOneDepth(mailboxId, ImmutableSet.of(PRIVATE_KEY))).isEmpty();
    }

    @Test
    public void getAnnotationsByKeysWithAllDepthShouldReturnEmptyIfDoNotFind() throws Exception {
        annotationMapper.insertAnnotation(mailboxId, SHARED_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_USER_ANNOTATION);

        assertThat(annotationMapper.getAnnotationsByKeysWithAllDepth(mailboxId, ImmutableSet.of(PRIVATE_KEY))).isEmpty();
    }

    @Test
    public void annotationShouldBeCaseInsentive() throws Exception {
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_ANNOTATION_WITH_KEY_UPPER);

        assertThat(annotationMapper.getAllAnnotations(mailboxId)).containsOnly(PRIVATE_ANNOTATION_WITH_KEY_UPPER);
    }

    @Test
    public void isExistedShouldReturnTrueIfAnnotationIsStored() throws Exception {
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_ANNOTATION);

        assertThat(annotationMapper.exist(mailboxId, PRIVATE_ANNOTATION)).isTrue();
    }

    @Test
    public void isExistedShouldReturnFalseIfAnnotationIsNotStored() throws Exception {
        assertThat(annotationMapper.exist(mailboxId, PRIVATE_ANNOTATION)).isFalse();
    }

    @Test
    public void countAnnotationShouldReturnZeroIfNoMoreAnnotationBelongToMailbox() throws Exception {
        assertThat(annotationMapper.countAnnotations(mailboxId)).isEqualTo(0);
    }

    @Test
    public void countAnnotationShouldReturnNumberOfAnnotationBelongToMailbox() throws Exception {
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_ANNOTATION_UPDATE);
        annotationMapper.insertAnnotation(mailboxId, SHARED_ANNOTATION);
        annotationMapper.insertAnnotation(mailboxId, PRIVATE_USER_ANNOTATION);

        assertThat(annotationMapper.countAnnotations(mailboxId)).isEqualTo(3);
    }
}
