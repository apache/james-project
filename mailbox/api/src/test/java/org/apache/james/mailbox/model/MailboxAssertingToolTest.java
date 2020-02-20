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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.Username;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


class MailboxAssertingToolTest {
    private static final Username USER = Username.of("user");
    private static final Username USER1 = Username.of("user1");

    private static final long UID_VALIDITY = 42;
    private static final TestId MAILBOX_ID = TestId.of(24);

    @Nested
    class MailboxAssertTest {
        @Test
        void isEqualToShouldNotFailWithEqualMailbox() {
            Mailbox mailbox1 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);
            Mailbox mailbox2 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);

            MailboxAssertingTool.assertThat(mailbox1).isEqualTo(mailbox2);
        }

        @Test
        void isEqualToShouldFailWithNotEqualNamespace() {
            Mailbox mailbox1 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);
            Mailbox mailbox2 = new Mailbox(new MailboxPath("other_namespace", USER, "name"), UID_VALIDITY, MAILBOX_ID);

            assertThatThrownBy(() -> MailboxAssertingTool.assertThat(mailbox1).isEqualTo(mailbox2))
                .isInstanceOf(AssertionError.class);
        }

        @Test
        void isEqualToShouldFailWithNotEqualUser() {
            Mailbox mailbox1 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);
            Mailbox mailbox2 = new Mailbox(new MailboxPath("namespace", Username.of("other_user"), "name"), UID_VALIDITY, MAILBOX_ID);

            assertThatThrownBy(() -> MailboxAssertingTool.assertThat(mailbox1).isEqualTo(mailbox2))
                .isInstanceOf(AssertionError.class);
        }

        @Test
        void isEqualToShouldFailWithNotEqualName() {
            Mailbox mailbox1 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);
            Mailbox mailbox2 = new Mailbox(new MailboxPath("namespace", USER, "other_name"), UID_VALIDITY, MAILBOX_ID);

            assertThatThrownBy(() -> MailboxAssertingTool.assertThat(mailbox1).isEqualTo(mailbox2))
                .isInstanceOf(AssertionError.class);
        }

        @Test
        void isEqualToShouldFailWithNotEqualId() {
            Mailbox mailbox1 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);
            Mailbox mailbox2 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, TestId.of(MAILBOX_ID.id + 1));

            assertThatThrownBy(() -> MailboxAssertingTool.assertThat(mailbox1).isEqualTo(mailbox2))
                .isInstanceOf(AssertionError.class);
        }

        @Test
        void isEqualToShouldFailWithNotEqualUidValidity() {
            Mailbox mailbox1 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);
            Mailbox mailbox2 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY + 1, MAILBOX_ID);

            assertThatThrownBy(() -> MailboxAssertingTool.assertThat(mailbox1).isEqualTo(mailbox2))
                .isInstanceOf(AssertionError.class);
        }

    }

    @Nested
    class MailboxSoftlyAssertTest {

        @Test
        void isEqualToShouldNotFailWithEqualMailbox() {
            Mailbox mailbox1 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);
            Mailbox mailbox2 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);

            SoftAssertions.assertSoftly(softly -> {
                MailboxAssertingTool.softly(softly)
                    .assertThat(mailbox1)
                    .isEqualTo(mailbox2);
            });
        }

        @Test
        void isEqualToShouldFailWithNotEqualNamespace() {
            Mailbox mailbox1 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);
            Mailbox mailbox2 = new Mailbox(new MailboxPath("other_namespace", USER, "name"), UID_VALIDITY, MAILBOX_ID);

            assertThatThrownBy(() -> {
                    SoftAssertions.assertSoftly(softly -> {
                        MailboxAssertingTool.softly(softly)
                            .assertThat(mailbox1)
                            .isEqualTo(mailbox2);
                    });
                })
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected NameSpace to be <other_namespace> but was <#private>");
        }

        @Test
        void isEqualToShouldFailWithNotEqualName() {
            Mailbox mailbox1 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);
            Mailbox mailbox2 = new Mailbox(MailboxPath.forUser(USER, "other_name"), UID_VALIDITY, MAILBOX_ID);

            assertThatThrownBy(() -> {
                    SoftAssertions.assertSoftly(softly -> {
                        MailboxAssertingTool.softly(softly)
                            .assertThat(mailbox1)
                            .isEqualTo(mailbox2);
                    });
                })
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected Name to be <other_name> but was <name>");
        }

        @Test
        void isEqualToShouldFailWithNotEqualId() {
            Mailbox mailbox1 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);
            Mailbox mailbox2 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, TestId.of(MAILBOX_ID.id + 1));

            assertThatThrownBy(() -> {
                    SoftAssertions.assertSoftly(softly -> {
                        MailboxAssertingTool.softly(softly)
                            .assertThat(mailbox1)
                            .isEqualTo(mailbox2);
                    });
                })
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected MailboxId to be <TestId{id=25}> but was <TestId{id=24}>");
        }

        @Test
        void isEqualToShouldFailWithNotEqualUidValidity() {
            Mailbox mailbox1 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);
            Mailbox mailbox2 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY + 1, MAILBOX_ID);

            assertThatThrownBy(() -> {
                    SoftAssertions.assertSoftly(softly -> {
                        MailboxAssertingTool.softly(softly)
                            .assertThat(mailbox1)
                            .isEqualTo(mailbox2);
                    });
                })
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected UID Validity to be <43> but was <42>");
        }

        @Test
        void isEqualToShouldFailWithNotSameSizeEntries() throws Exception {
            Mailbox mailbox1 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);
            Mailbox mailbox2 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);

            mailbox1.setACL(new MailboxACL(
                new MailboxACL.Entry(USER.asString(), MailboxACL.Right.Write)));
            mailbox2.setACL(new MailboxACL(
                new MailboxACL.Entry(USER.asString(), MailboxACL.Right.Write),
                new MailboxACL.Entry(USER1.asString(), MailboxACL.Right.Read)));

            assertThatThrownBy(() -> {
                    SoftAssertions.assertSoftly(softly -> {
                        MailboxAssertingTool.softly(softly)
                            .assertThat(mailbox1)
                            .isEqualTo(mailbox2);
                    });
                })
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected ACL to be <MailboxACL{entries={user=w, user1=r}}> but was <MailboxACL{entries={user=w}}");
        }

        @Test
        void isEqualToShouldFailWithSameSizeButDifferentEntries() throws Exception {
            Mailbox mailbox1 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);
            Mailbox mailbox2 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);

            mailbox1.setACL(new MailboxACL(
                new MailboxACL.Entry(USER.asString(), MailboxACL.Right.Write)));
            mailbox2.setACL(new MailboxACL(
                new MailboxACL.Entry(USER1.asString(), MailboxACL.Right.Read)));

            assertThatThrownBy(() -> {
                    SoftAssertions.assertSoftly(softly -> {
                        MailboxAssertingTool.softly(softly)
                            .assertThat(mailbox1)
                            .isEqualTo(mailbox2);
                    });
                })
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected ACL to be <MailboxACL{entries={user1=r}}> but was <MailboxACL{entries={user=w}}");
        }

        @Test
        void isEqualToShouldPassWithSameSizeEntriesButDifferentOrder() throws Exception {
            Mailbox mailbox1 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);
            Mailbox mailbox2 = new Mailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY, MAILBOX_ID);

            mailbox1.setACL(new MailboxACL(
                new MailboxACL.Entry(USER1.asString(), MailboxACL.Right.Read),
                new MailboxACL.Entry(USER.asString(), MailboxACL.Right.Write)));
            mailbox2.setACL(new MailboxACL(
                new MailboxACL.Entry(USER.asString(), MailboxACL.Right.Write),
                new MailboxACL.Entry(USER1.asString(), MailboxACL.Right.Read)));

            SoftAssertions.assertSoftly(softly -> {
                MailboxAssertingTool.softly(softly)
                    .assertThat(mailbox1)
                    .isEqualTo(mailbox2);
            });
        }
    }
}
