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

import org.assertj.core.api.SoftAssertions;

import com.google.common.base.Preconditions;

public class MailboxSoftlyAssert {

    @FunctionalInterface
    public interface RequireActualMailbox {
        MailboxAssertingStage assertThat(Mailbox actual);
    }

    public static class MailboxAssertingStage {
        private final SoftAssertions softly;
        private final Mailbox actual;

        MailboxAssertingStage(SoftAssertions softly, Mailbox actual) {
            Preconditions.checkNotNull(softly);
            Preconditions.checkNotNull(actual);

            this.softly = softly;
            this.actual = actual;
        }

        public void isEqualTo(Mailbox expected) {
            Preconditions.checkNotNull(expected);

            softly.assertThat(actual.getMailboxId())
                .withFailMessage("Expected MailboxId to be <%s> but was <%s>", expected.getMailboxId(), actual.getMailboxId())
                .isEqualTo(expected.getMailboxId());
            softly.assertThat(actual.getName())
                .withFailMessage("Expected Name to be <%s> but was <%s>", expected.getName(), actual.getName())
                .isEqualTo(expected.getName());
            softly.assertThat(actual.getUidValidity())
                .withFailMessage("Expected UID Validity to be <%s> but was <%s>", expected.getUidValidity(), actual.getUidValidity())
                .isEqualTo(expected.getUidValidity());
            softly.assertThat(actual.getUser())
                .withFailMessage("Expected User to be <%s> but was <%s>", expected.getUser(), actual.getUser())
                .isEqualTo(expected.getUser());
            softly.assertThat(actual.getNamespace())
                .withFailMessage("Expected NameSpace to be <%s> but was <%s>", expected.getNamespace(), actual.getNamespace())
                .isEqualTo(expected.getNamespace());
            softly.assertThat(actual.getACL())
                .withFailMessage("Expected ACL to be <%s> but was <%s>", expected.getACL(), actual.getACL())
                .isEqualTo(expected.getACL());
        }
    }

    public static RequireActualMailbox softly(SoftAssertions softly) {
        return actual -> new MailboxAssertingStage(softly, actual);
    }
}
