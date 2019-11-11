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

package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MailboxAnnotationTest {
    private static final MailboxAnnotationKey ANNOTATION_KEY = new MailboxAnnotationKey("/private/comment");
    private static final String ANNOTATION_VALUE = "anyValue";

    @Test
    void sizeOfAnnotationShouldBeReturnLengthOfValue() {
        MailboxAnnotation mailboxAnnotation = MailboxAnnotation.newInstance(ANNOTATION_KEY, ANNOTATION_VALUE);

        assertThat(mailboxAnnotation.size()).isEqualTo(8);
    }

    @Test
    void sizeOfNilAnnotationShouldBeZero() {
        MailboxAnnotation mailboxAnnotation = MailboxAnnotation.nil(ANNOTATION_KEY);

        assertThat(mailboxAnnotation.size()).isEqualTo(0);
    }
    
    @Test
    void newInstanceShouldThrowsExceptionWithNullKey() {
        assertThatThrownBy(() -> MailboxAnnotation.newInstance(null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void newInstanceShouldThrowsExceptionWithNullValue() {
        assertThatThrownBy(() -> MailboxAnnotation.newInstance(ANNOTATION_KEY, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nilInstanceShouldReturnAbsentValue() {
        MailboxAnnotation annotation = MailboxAnnotation.nil(ANNOTATION_KEY);

        assertThat(annotation.getValue()).isEmpty();
    }

    @Test
    void isNilShouldReturnTrueForNilObject() {
        MailboxAnnotation nilAnnotation = MailboxAnnotation.nil(ANNOTATION_KEY);
        assertThat(nilAnnotation.isNil()).isTrue();
    }

    @Test
    void isNilShouldReturnFalseForNotNilObject() {
        MailboxAnnotation nilAnnotation = MailboxAnnotation.newInstance(ANNOTATION_KEY, ANNOTATION_VALUE);
        assertThat(nilAnnotation.isNil()).isFalse();
    }

    @Test
    void newInstanceMailboxAnnotationShouldCreateNewInstance() {
        MailboxAnnotation annotation = MailboxAnnotation.newInstance(ANNOTATION_KEY, ANNOTATION_VALUE);

        assertThat(annotation.getKey()).isEqualTo(ANNOTATION_KEY);
        assertThat(annotation.getValue()).contains(ANNOTATION_VALUE);
    }

}
