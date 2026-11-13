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

package org.apache.james.mailbox.backup;

import static org.apache.james.mailbox.backup.MailboxMessageFixture.MAILBOX_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MAILBOX_1_SUB_1;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class MailboxWithAnnotationsTest {
    private static final ImmutableList<MailboxAnnotation> ANNOTATIONS = ImmutableList.of(
        MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment"), "value"));

    @Test
    void equalsShouldBeTrueForTheSameValues() {
        assertThat(new MailboxWithAnnotations(MAILBOX_1, ANNOTATIONS))
            .isEqualTo(new MailboxWithAnnotations(MAILBOX_1, ANNOTATIONS))
            .hasSameHashCodeAs(new MailboxWithAnnotations(MAILBOX_1, ANNOTATIONS));
    }

    @Test
    void equalsShouldBeFalseForAnotherMailbox() {
        assertThat(new MailboxWithAnnotations(MAILBOX_1, ANNOTATIONS))
            .isNotEqualTo(new MailboxWithAnnotations(MAILBOX_1_SUB_1, ANNOTATIONS));
    }
}
