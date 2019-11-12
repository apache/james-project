/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mailbox.DefaultMailboxes;
import org.junit.jupiter.api.Test;
import org.apache.james.mailbox.exception.HasEmptyMailboxNameInHierarchyException;
import org.apache.james.mailbox.exception.MailboxNameException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;

import com.google.common.base.Strings;

import nl.jqno.equalsverifier.EqualsVerifier;

class MailboxPathTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MailboxPath.class)
            .verify();
    }

    @Test
    void getHierarchyLevelsShouldBeOrdered() {
        assertThat(MailboxPath.forUser("user", "inbox.folder.subfolder")
            .getHierarchyLevels('.'))
            .containsExactly(
                MailboxPath.forUser("user", "inbox"),
                MailboxPath.forUser("user", "inbox.folder"),
                MailboxPath.forUser("user", "inbox.folder.subfolder"));
    }

    @Test
    void getHierarchyLevelsShouldReturnPathWhenOneLevel() {
        assertThat(MailboxPath.forUser("user", "inbox")
            .getHierarchyLevels('.'))
            .containsExactly(
                MailboxPath.forUser("user", "inbox"));
    }

    @Test
    void getHierarchyLevelsShouldReturnPathWhenEmptyName() {
        assertThat(MailboxPath.forUser("user", "")
            .getHierarchyLevels('.'))
            .containsExactly(
                MailboxPath.forUser("user", ""));
    }

    @Test
    void getHierarchyLevelsShouldReturnPathWhenNullName() {
        assertThat(MailboxPath.forUser("user", null)
            .getHierarchyLevels('.'))
            .containsExactly(
                MailboxPath.forUser("user", null));
    }

    @Test
    void sanitizeShouldNotThrowOnNullMailboxName() {
        assertThat(MailboxPath.forUser("user", null)
            .sanitize('.'))
            .isEqualTo(
                MailboxPath.forUser("user", null));
    }

    @Test
    void sanitizeShouldReturnEmptyWhenEmpty() {
        assertThat(MailboxPath.forUser("user", "")
            .sanitize('.'))
            .isEqualTo(
                MailboxPath.forUser("user", ""));
    }

    @Test
    void sanitizeShouldRemoveMaximumOneTrailingDelimiterWhenAlone() {
        assertThat(MailboxPath.forUser("user", ".")
            .sanitize('.'))
            .isEqualTo(
                MailboxPath.forUser("user", ""));
    }

    @Test
    void sanitizeShouldPreserveHeadingDelimiter() {
        assertThat(MailboxPath.forUser("user", ".a")
            .sanitize('.'))
            .isEqualTo(
                MailboxPath.forUser("user", ".a"));
    }

    @Test
    void sanitizeShouldRemoveTrailingDelimiter() {
        assertThat(MailboxPath.forUser("user", "a.")
            .sanitize('.'))
            .isEqualTo(
                MailboxPath.forUser("user", "a"));
    }

    @Test
    void sanitizeShouldRemoveMaximumOneTrailingDelimiter() {
        assertThat(MailboxPath.forUser("user", "a..")
            .sanitize('.'))
            .isEqualTo(
                MailboxPath.forUser("user", "a."));
    }

    @Test
    void sanitizeShouldPreserveRedundantDelimiters() {
        assertThat(MailboxPath.forUser("user", "a..a")
            .sanitize('.'))
            .isEqualTo(
                MailboxPath.forUser("user", "a..a"));
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeFalseIfSingleLevelPath() {
        assertThat(MailboxPath.forUser("user", "a")
            .hasEmptyNameInHierarchy('.'))
            .isFalse();
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeFalseIfNestedLevelWithNonEmptyNames() {
        assertThat(MailboxPath.forUser("user", "a.b.c")
            .hasEmptyNameInHierarchy('.'))
            .isFalse();
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeTrueIfEmptyPath() {
        assertThat(MailboxPath.forUser("user", "")
            .hasEmptyNameInHierarchy('.'))
            .isTrue();
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeTrueIfPathWithTwoEmptyNames() {
        assertThat(MailboxPath.forUser("user", ".")
            .hasEmptyNameInHierarchy('.'))
            .isTrue();
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeTrueIfPathWithAnEmptyNameBetweenTwoNames() {
        assertThat(MailboxPath.forUser("user", "a..b")
            .hasEmptyNameInHierarchy('.'))
            .isTrue();
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeTrueIfPathWithHeadingEmptyNames() {
        assertThat(MailboxPath.forUser("user", "..a")
            .hasEmptyNameInHierarchy('.'))
            .isTrue();
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeTrueIfPathWithATrailingEmptyName() {
        assertThat(MailboxPath.forUser("user", "a.")
            .hasEmptyNameInHierarchy('.'))
            .isTrue();
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeTrueIfPathWithTrailingEmptyNames() {
        assertThat(MailboxPath.forUser("user", "a..")
            .hasEmptyNameInHierarchy('.'))
            .isTrue();
    }

    @Test
    void assertAcceptableShouldThrowOnDoubleSeparator() {
        assertThatThrownBy(() -> MailboxPath.forUser("user", "a..b")
                .assertAcceptable('.'))
            .isInstanceOf(HasEmptyMailboxNameInHierarchyException.class);
    }

    @Test
    void assertAcceptableShouldThrowOnAnd() {
        assertThatThrownBy(() -> MailboxPath.forUser("user", "a&b")
                .assertAcceptable('.'))
            .isInstanceOf(MailboxNameException.class);
    }

    @Test
    void assertAcceptableShouldThrowOnSharp() {
        assertThatThrownBy(() -> MailboxPath.forUser("user", "a#b")
                .assertAcceptable('.'))
            .isInstanceOf(MailboxNameException.class);
    }

    @Test
    void assertAcceptableShouldThrowOnPercent() {
        assertThatThrownBy(() -> MailboxPath.forUser("user", "a%b")
                .assertAcceptable('.'))
            .isInstanceOf(MailboxNameException.class);
    }

    @Test
    void assertAcceptableShouldThrowOnWildcard() {
        assertThatThrownBy(() -> MailboxPath.forUser("user", "a*b")
                .assertAcceptable('.'))
            .isInstanceOf(MailboxNameException.class);
    }

    @Test
    void assertAcceptableShouldThrowOnTooLongMailboxName() {
        assertThatThrownBy(() -> MailboxPath.forUser("user", Strings.repeat("a", 201))
                .assertAcceptable('.'))
            .isInstanceOf(TooLongMailboxNameException.class);
    }

    @Test
    void assertAcceptableShouldNotThrowOnNotTooLongMailboxName() {
        assertThatCode(() -> MailboxPath.forUser("user", Strings.repeat("a", 200))
                .assertAcceptable('.'))
            .doesNotThrowAnyException();
    }

    @Test
    void isInboxShouldReturnTrueWhenINBOX() {
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, "user", DefaultMailboxes.INBOX);
        assertThat(mailboxPath.isInbox()).isTrue();
    }

    @Test
    void isInboxShouldReturnTrueWhenINBOXWithOtherCase() {
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, "user", "InBoX");
        assertThat(mailboxPath.isInbox()).isTrue();
    }

    @Test
    void isInboxShouldReturnFalseWhenOtherThanInbox() {
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, "user", DefaultMailboxes.ARCHIVE);
        assertThat(mailboxPath.isInbox()).isFalse();
    }
}
