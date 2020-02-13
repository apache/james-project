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

import java.util.Objects;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.SoftAssertions;

import com.google.common.base.Preconditions;

public class MailboxAssertingTool {

    public static class MailboxAssert extends AbstractAssert<MailboxAssert, Mailbox> {

        private MailboxAssert(Mailbox actual) {
            super(actual, MailboxAssert.class);
        }

        public MailboxAssert isEqualTo(Mailbox expected) {
            isNotNull();
            if (!Objects.equals(actual.getMailboxId(), expected.getMailboxId())) {
                failWithMessage(mailboxIdFailMessage(expected, actual));
            }
            if (!Objects.equals(actual.getNamespace(), expected.getNamespace())) {
                failWithMessage(namespaceFailMessage(expected, actual));
            }
            if (!Objects.equals(actual.getUser(), expected.getUser())) {
                failWithMessage(userFailMessage(expected, actual));
            }
            if (!Objects.equals(actual.getName(), expected.getName())) {
                failWithMessage(nameFailMessage(expected, actual));
            }
            if (!Objects.equals(actual.getACL(), expected.getACL())) {
                failWithMessage(aclFailMessage(expected, actual));
            }
            if (actual.getUidValidity() != expected.getUidValidity()) {
                failWithMessage(uidValidityFailMessage(expected, actual));
            }
            return this;
        }
    }

    public static class MailboxSoftlyAssert {

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
                    .withFailMessage(mailboxIdFailMessage(expected, actual))
                    .isEqualTo(expected.getMailboxId());
                softly.assertThat(actual.getName())
                    .withFailMessage(nameFailMessage(expected, actual))
                    .isEqualTo(expected.getName());
                softly.assertThat(actual.getUidValidity())
                    .withFailMessage(uidValidityFailMessage(expected, actual))
                    .isEqualTo(expected.getUidValidity());
                softly.assertThat(actual.getUser())
                    .withFailMessage(userFailMessage(expected, actual))
                    .isEqualTo(expected.getUser());
                softly.assertThat(actual.getNamespace())
                    .withFailMessage(namespaceFailMessage(expected, actual))
                    .isEqualTo(expected.getNamespace());
                softly.assertThat(actual.getACL())
                    .withFailMessage(aclFailMessage(expected, actual))
                    .isEqualTo(expected.getACL());
            }
        }
    }

    public static MailboxAssert assertThat(Mailbox actual) {
        return new MailboxAssert(actual);
    }

    public static MailboxSoftlyAssert.RequireActualMailbox softly(SoftAssertions softly) {
        return actual -> new MailboxSoftlyAssert.MailboxAssertingStage(softly, actual);
    }

    private static String mailboxIdFailMessage(Mailbox expected, Mailbox actual) {
        return String.format("Expected MailboxId to be <%s> but was <%s>", expected.getMailboxId(), actual.getMailboxId());
    }

    private static String namespaceFailMessage(Mailbox expected, Mailbox actual) {
        return String.format("Expected NameSpace to be <%s> but was <%s>", expected.getNamespace(), actual.getNamespace());
    }

    private static String userFailMessage(Mailbox expected, Mailbox actual) {
        return String.format("Expected User to be <%s> but was <%s>", expected.getUser(), actual.getUser());
    }

    private static String nameFailMessage(Mailbox expected, Mailbox actual) {
        return String.format("Expected Name to be <%s> but was <%s>", expected.getName(), actual.getName());
    }

    private static String aclFailMessage(Mailbox expected, Mailbox actual) {
        return String.format("Expected ACL to be <%s> but was <%s>", expected.getACL(), actual.getACL());
    }

    private static String uidValidityFailMessage(Mailbox expected, Mailbox actual) {
        return String.format("Expected UID Validity to be <%s> but was <%s>", expected.getUidValidity(), actual.getUidValidity());
    }
}
