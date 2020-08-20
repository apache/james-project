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

import org.apache.james.core.Username;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.exception.HasEmptyMailboxNameInHierarchyException;
import org.apache.james.mailbox.exception.MailboxNameException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;

import nl.jqno.equalsverifier.EqualsVerifier;

class MailboxPathTest {
    private static final Username USER = Username.of("user");

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MailboxPath.class)
            .verify();
    }

    @Test
    void asStringShouldFormatUser() {
        assertThat(MailboxPath.forUser(USER, "inbox.folder.subfolder").asString())
            .isEqualTo("#private:user:inbox.folder.subfolder");
    }

    @Test
    void getNameShouldReturnSubfolder() {
        assertThat(MailboxPath.forUser(USER, "inbox.folder.subfolder").getName('.'))
            .isEqualTo("subfolder");
    }

    @Test
    void getNameShouldNoopWhenNoDelimiter() {
        assertThat(MailboxPath.forUser(USER, "name").getName('.'))
            .isEqualTo("name");
    }

    @Test
    void getNameShouldNoopWhenEmpty() {
        assertThat(MailboxPath.forUser(USER, "").getName('.'))
            .isEqualTo("");
    }

    @Test
    void getHierarchyLevelsShouldBeOrdered() {
        assertThat(MailboxPath.forUser(USER, "inbox.folder.subfolder")
            .getHierarchyLevels('.'))
            .containsExactly(
                MailboxPath.forUser(USER, "inbox"),
                MailboxPath.forUser(USER, "inbox.folder"),
                MailboxPath.forUser(USER, "inbox.folder.subfolder"));
    }

    @Test
    void childShouldConcatenateChildNameWithParentForlder() {
        assertThat(MailboxPath.forUser(USER, "folder")
            .child("toto", '.'))
            .isEqualTo(MailboxPath.forUser(USER, "folder.toto"));
    }

    @Test
    void childShouldThrowWhenNull() {
        MailboxPath path = MailboxPath.forUser(USER, "folder");
        assertThatThrownBy(() -> path.child(null, '.'))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void childShouldThrowWhenEmpty() {
        MailboxPath path = MailboxPath.forUser(USER, "folder");
        assertThatThrownBy(() -> path.child("", '.'))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void childShouldThrowWhenContainsDelimiter() {
        MailboxPath path = MailboxPath.forUser(USER, "folder");
        assertThatThrownBy(() -> path.child("a.b", '.'))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getHierarchyLevelsShouldReturnPathWhenOneLevel() {
        assertThat(MailboxPath.forUser(USER, "inbox")
            .getHierarchyLevels('.'))
            .containsExactly(
                MailboxPath.forUser(USER, "inbox"));
    }

    @Test
    void getHierarchyLevelsShouldReturnPathWhenEmptyName() {
        assertThat(MailboxPath.forUser(USER, "")
            .getHierarchyLevels('.'))
            .containsExactly(
                MailboxPath.forUser(USER, ""));
    }

    @Test
    void getHierarchyLevelsShouldReturnPathWhenNullName() {
        assertThat(MailboxPath.forUser(USER, null)
            .getHierarchyLevels('.'))
            .containsExactly(
                MailboxPath.forUser(USER, null));
    }

    @Test
    void sanitizeShouldNotThrowOnNullMailboxName() {
        assertThat(MailboxPath.forUser(USER, null)
            .sanitize('.'))
            .isEqualTo(
                MailboxPath.forUser(USER, null));
    }

    @Test
    void sanitizeShouldReturnEmptyWhenEmpty() {
        assertThat(MailboxPath.forUser(USER, "")
            .sanitize('.'))
            .isEqualTo(
                MailboxPath.forUser(USER, ""));
    }

    @Test
    void sanitizeShouldRemoveMaximumOneTrailingDelimiterWhenAlone() {
        assertThat(MailboxPath.forUser(USER, ".")
            .sanitize('.'))
            .isEqualTo(
                MailboxPath.forUser(USER, ""));
    }

    @Test
    void sanitizeShouldPreserveHeadingDelimiter() {
        assertThat(MailboxPath.forUser(USER, ".a")
            .sanitize('.'))
            .isEqualTo(
                MailboxPath.forUser(USER, ".a"));
    }

    @Test
    void sanitizeShouldRemoveTrailingDelimiter() {
        assertThat(MailboxPath.forUser(USER, "a.")
            .sanitize('.'))
            .isEqualTo(
                MailboxPath.forUser(USER, "a"));
    }

    @Test
    void sanitizeShouldRemoveMaximumOneTrailingDelimiter() {
        assertThat(MailboxPath.forUser(USER, "a..")
            .sanitize('.'))
            .isEqualTo(
                MailboxPath.forUser(USER, "a."));
    }

    @Test
    void sanitizeShouldPreserveRedundantDelimiters() {
        assertThat(MailboxPath.forUser(USER, "a..a")
            .sanitize('.'))
            .isEqualTo(
                MailboxPath.forUser(USER, "a..a"));
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeFalseIfSingleLevelPath() {
        assertThat(MailboxPath.forUser(USER, "a")
            .hasEmptyNameInHierarchy('.'))
            .isFalse();
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeFalseIfNestedLevelWithNonEmptyNames() {
        assertThat(MailboxPath.forUser(USER, "a.b.c")
            .hasEmptyNameInHierarchy('.'))
            .isFalse();
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeTrueIfEmptyPath() {
        assertThat(MailboxPath.forUser(USER, "")
            .hasEmptyNameInHierarchy('.'))
            .isTrue();
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeTrueIfPathWithTwoEmptyNames() {
        assertThat(MailboxPath.forUser(USER, ".")
            .hasEmptyNameInHierarchy('.'))
            .isTrue();
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeTrueIfPathWithAnEmptyNameBetweenTwoNames() {
        assertThat(MailboxPath.forUser(USER, "a..b")
            .hasEmptyNameInHierarchy('.'))
            .isTrue();
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeTrueIfPathWithHeadingEmptyNames() {
        assertThat(MailboxPath.forUser(USER, "..a")
            .hasEmptyNameInHierarchy('.'))
            .isTrue();
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeTrueIfPathWithATrailingEmptyName() {
        assertThat(MailboxPath.forUser(USER, "a.")
            .hasEmptyNameInHierarchy('.'))
            .isTrue();
    }

    @Test
    void hasEmptyNameInHierarchyShouldBeTrueIfPathWithTrailingEmptyNames() {
        assertThat(MailboxPath.forUser(USER, "a..")
            .hasEmptyNameInHierarchy('.'))
            .isTrue();
    }

    @Test
    void assertAcceptableShouldThrowOnDoubleSeparator() {
        assertThatThrownBy(() -> MailboxPath.forUser(USER, "a..b")
                .assertAcceptable('.'))
            .isInstanceOf(HasEmptyMailboxNameInHierarchyException.class);
    }

    @Test
    void assertAcceptableShouldThrowOnAnd() {
        assertThatThrownBy(() -> MailboxPath.forUser(USER, "a&b")
                .assertAcceptable('.'))
            .isInstanceOf(MailboxNameException.class);
    }

    @Test
    void assertAcceptableShouldThrowOnSharp() {
        assertThatThrownBy(() -> MailboxPath.forUser(USER, "a#b")
                .assertAcceptable('.'))
            .isInstanceOf(MailboxNameException.class);
    }

    @Test
    void assertAcceptableShouldThrowOnPercent() {
        assertThatThrownBy(() -> MailboxPath.forUser(USER, "a%b")
                .assertAcceptable('.'))
            .isInstanceOf(MailboxNameException.class);
    }

    @Test
    void assertAcceptableShouldThrowOnWildcard() {
        assertThatThrownBy(() -> MailboxPath.forUser(USER, "a*b")
                .assertAcceptable('.'))
            .isInstanceOf(MailboxNameException.class);
    }

    @Test
    void assertAcceptableShouldThrowOnTooLongMailboxName() {
        assertThatThrownBy(() -> MailboxPath.forUser(USER, Strings.repeat("a", 201))
                .assertAcceptable('.'))
            .isInstanceOf(TooLongMailboxNameException.class);
    }

    @Test
    void assertAcceptableShouldNotThrowOnNotTooLongMailboxName() {
        assertThatCode(() -> MailboxPath.forUser(USER, Strings.repeat("a", 200))
                .assertAcceptable('.'))
            .doesNotThrowAnyException();
    }

    @Test
    void isInboxShouldReturnTrueWhenINBOX() {
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, DefaultMailboxes.INBOX);
        assertThat(mailboxPath.isInbox()).isTrue();
    }

    @Test
    void isInboxShouldReturnTrueWhenINBOXWithOtherCase() {
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, "InBoX");
        assertThat(mailboxPath.isInbox()).isTrue();
    }

    @Test
    void isInboxShouldReturnFalseWhenOtherThanInbox() {
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, DefaultMailboxes.ARCHIVE);
        assertThat(mailboxPath.isInbox()).isFalse();
    }
}
