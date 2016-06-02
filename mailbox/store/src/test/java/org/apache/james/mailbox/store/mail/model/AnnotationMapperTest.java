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

import java.util.List;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.xenei.junit.contract.Contract;
import org.xenei.junit.contract.ContractTest;
import org.xenei.junit.contract.IProducer;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

@Contract(MapperProvider.class)
public class AnnotationMapperTest<T extends MapperProvider> {
    private static final MailboxAnnotation PRIVATE_ANNOTATION = MailboxAnnotation.newInstance("/private/comment", "My private comment");
    private static final MailboxAnnotation PRIVATE_USER_ANNOTATION = MailboxAnnotation.newInstance("/private/comment/user", "My private comment");
    private static final MailboxAnnotation PRIVATE_ANNOTATION_UPDATE = MailboxAnnotation.newInstance("/private/comment", "My updated private comment");
    private static final MailboxAnnotation SHARED_ANNOTATION =  MailboxAnnotation.newInstance("/shared/comment", "My shared comment");

    private static final List<MailboxAnnotation> PRIVATE_AND_SHARED_ANNOTATIONS = Lists.newArrayList(PRIVATE_ANNOTATION, SHARED_ANNOTATION);


    private IProducer<T> producer;
    private AnnotationMapper annotationMapper;

    @Rule
    public ExpectedException expected = ExpectedException.none();

    @Contract.Inject
    public final void setProducer(IProducer<T> producer) throws MailboxException {
        this.producer = producer;
        this.annotationMapper = producer.newInstance().createAnnotationMapper();
    }

    @After
    public void tearDown() {
        producer.cleanUp();
    }

    @ContractTest
    public void insertAnnotationShouldThrowExceptionWithNilData() {
        expected.expect(IllegalArgumentException.class);
        annotationMapper.insertAnnotation(MailboxAnnotation.nil("/private/comment"));
    }

    @ContractTest
    public void insertAnnotationShouldCreateNewAnnotation() throws MailboxException {
        annotationMapper.insertAnnotation(PRIVATE_ANNOTATION);

        assertThat(annotationMapper.getAllAnnotations()).containsExactly(PRIVATE_ANNOTATION);
    }

    @ContractTest
    public void insertAnnotationShouldUpdateExistedAnnotation() throws MailboxException {
        annotationMapper.insertAnnotation(PRIVATE_ANNOTATION);
        annotationMapper.insertAnnotation(PRIVATE_ANNOTATION_UPDATE);

        assertThat(annotationMapper.getAllAnnotations()).containsExactly(PRIVATE_ANNOTATION_UPDATE);
    }

    @ContractTest
    public void deleteAnnotationShouldDeleteStoredAnnotation() throws MailboxException {
        annotationMapper.insertAnnotation(PRIVATE_ANNOTATION);
        annotationMapper.deleteAnnotation("/private/comment");

        assertThat(annotationMapper.getAllAnnotations()).isEmpty();
    }

    @ContractTest
    public void getEmptyAnnotationsWithNonStoredAnnotations() throws MailboxException {
        assertThat(annotationMapper.getAllAnnotations()).isEmpty();
    }

    @ContractTest
    public void getAllAnnotationsShouldRetrieveStoredAnnotations() throws MailboxException {
        annotationMapper.insertAnnotation(PRIVATE_ANNOTATION);
        annotationMapper.insertAnnotation(SHARED_ANNOTATION);

        assertThat(annotationMapper.getAllAnnotations()).containsExactlyElementsOf(PRIVATE_AND_SHARED_ANNOTATIONS);
    }

    @ContractTest
    public void getAnnotationsByKeysShouldReturnStoredAnnotationWithFilter() throws MailboxException {
        annotationMapper.insertAnnotation(PRIVATE_ANNOTATION);
        annotationMapper.insertAnnotation(PRIVATE_USER_ANNOTATION);

        assertThat(annotationMapper.getAnnotationsByKeys(ImmutableSet.of("/private/comment")))
            .containsOnly(PRIVATE_ANNOTATION);
    }

}
