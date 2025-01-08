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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.apache.james.core.Username;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.exception.HasEmptyMailboxNameInHierarchyException;
import org.apache.james.mailbox.exception.MailboxNameException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;

import nl.jqno.equalsverifier.EqualsVerifier;

class MailboxPathTest {
    @Nested
    public class DotDelimiter extends TestBase {
        @Override
        public char folderDelimiter() {
            return MailboxConstants.MailboxFolderDelimiter.DOT.value;
        }
    }

    @Nested
    public class SlashDelimiter extends TestBase {
        @Override
        public char folderDelimiter() {
            return MailboxConstants.MailboxFolderDelimiter.SLASH.value;
        }
    }

    @Nested
    public class PipeDelimiter extends TestBase {
        @Override
        public char folderDelimiter() {
            return MailboxConstants.MailboxFolderDelimiter.PIPE.value;
        }
    }

    @Nested
    public class CommaDelimiter extends TestBase {
        @Override
        public char folderDelimiter() {
            return MailboxConstants.MailboxFolderDelimiter.COMMA.value;
        }
    }

    @Nested
    public class ColonDelimiter extends TestBase {
        @Override
        public char folderDelimiter() {
            return MailboxConstants.MailboxFolderDelimiter.COLON.value;
        }
    }

    @Nested
    public class SemicolonDelimiter extends TestBase {
        @Override
        public char folderDelimiter() {
            return MailboxConstants.MailboxFolderDelimiter.SEMICOLON.value;
        }
    }

    abstract static class TestBase extends MailboxFolderDelimiterAwareTest {
        private static final Username USER = Username.of("user");
        private static final Username BUGGY_USER = Username.of("buggy:bob");

        @Test
        void shouldMatchBeanContract() {
            EqualsVerifier.forClass(MailboxPath.class)
                    .withNonnullFields("namespace")
                    .verify();
        }

        static Stream<Arguments> parseShouldYieldCorrectResults() {
            return Stream.of(
                    Arguments.of(MailboxPath.forUser(USER, "test")),
                    Arguments.of(MailboxPath.forUser(USER, "a:b")),
                    Arguments.of(MailboxPath.forUser(USER, "a;b")),
                    Arguments.of(MailboxPath.forUser(USER, "a;;b")),
                    Arguments.of(MailboxPath.forUser(USER, "a:b:c:")),
                    Arguments.of(MailboxPath.forUser(USER, "a:b:c:")),
                    Arguments.of(MailboxPath.forUser(USER, ":")),
                    Arguments.of(MailboxPath.forUser(USER, ";")),
                    Arguments.of(MailboxPath.forUser(USER, "")),
                    Arguments.of(MailboxPath.inbox(USER)),
                    Arguments.of(MailboxPath.inbox(Username.of("a;b"))),
                    Arguments.of(MailboxPath.inbox(Username.of(";"))),
                    Arguments.of(MailboxPath.inbox(Username.of(":"))),
                    Arguments.of(MailboxPath.inbox(Username.of("a;;a"))),
                    Arguments.of(MailboxPath.inbox(Username.of("a:::;:::;:;;;a"))),
                    Arguments.of(MailboxPath.inbox(Username.of("a::a"))),
                    Arguments.of(MailboxPath.inbox(Username.of("a/a"))),
                    Arguments.of(MailboxPath.inbox(Username.of("a//a"))),
                    Arguments.of(MailboxPath.inbox(Username.of("a/:a"))),
                    Arguments.of(MailboxPath.inbox(Username.of("a/;a"))),
                    Arguments.of(MailboxPath.inbox(Username.of("a/:::;;/:://:;//:/a"))),
                    Arguments.of(MailboxPath.inbox(BUGGY_USER)),
                    Arguments.of(new MailboxPath("#whatever", USER, "whatever")),
                    Arguments.of(new MailboxPath(null, USER, "whatever")));
        }

        @ParameterizedTest
        @MethodSource
        void parseShouldYieldCorrectResults(MailboxPath mailboxPath) {
            assertThat(MailboxPath.parseEscaped(mailboxPath.asEscapedString()))
                    .contains(mailboxPath);
        }

        @Test
        void asStringShouldFormatUser() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("inbox.folder.subfolder")).asString())
                    .isEqualTo(adjustToActiveFolderDelimiter("#private:user:inbox.folder.subfolder"));
        }

        @Test
        void getNameShouldReturnSubfolder() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("inbox.folder.subfolder")).getName(folderDelimiter()))
                    .isEqualTo("subfolder");
        }

        @Test
        void getNameShouldNoopWhenNoDelimiter() {
            assertThat(MailboxPath.forUser(USER, "name").getName(folderDelimiter()))
                    .isEqualTo("name");
        }

        @Test
        void getNameShouldNoopWhenEmpty() {
            assertThat(MailboxPath.forUser(USER, "").getName(folderDelimiter()))
                    .isEqualTo("");
        }

        @Test
        void getNameShouldNoopWhenBlank() {
            assertThat(MailboxPath.forUser(USER, "  ").getName(folderDelimiter()))
                    .isEqualTo("  ");
        }

        @Test
        void getHierarchyLevelsShouldBeOrdered() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("inbox.folder.subfolder"))
                    .getHierarchyLevels(folderDelimiter()))
                    .containsExactly(
                            MailboxPath.forUser(USER, "inbox"),
                            MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("inbox.folder")),
                            MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("inbox.folder.subfolder")));
        }

        @Test
        void childShouldConcatenateChildNameWithParentFolder() {
            assertThat(MailboxPath.forUser(USER, "folder")
                    .child("toto", folderDelimiter()))
                    .isEqualTo(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("folder.toto")));
        }

        @Test
        void childShouldThrowWhenNull() {
            MailboxPath path = MailboxPath.forUser(USER, "folder");
            assertThatThrownBy(() -> path.child(null, folderDelimiter()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void childShouldThrowWhenEmpty() {
            MailboxPath path = MailboxPath.forUser(USER, "folder");
            assertThatThrownBy(() -> path.child("", folderDelimiter()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldThrowWhenLineBreak() {
            assertThatThrownBy(() -> MailboxPath.forUser(USER, "a\r\n [ALERT] that's bad").assertAcceptable(folderDelimiter()))
                    .isInstanceOf(MailboxNameException.class);
        }

        @Test
        void childShouldThrowWhenBlank() {
            MailboxPath path = MailboxPath.forUser(USER, "folder");
            assertThatThrownBy(() -> path.child(" ", folderDelimiter()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void childShouldThrowWhenContainsDelimiter() {
            MailboxPath path = MailboxPath.forUser(USER, "folder");
            assertThatThrownBy(() -> path.child(adjustToActiveFolderDelimiter("a.b"), folderDelimiter()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void getHierarchyLevelsShouldReturnPathWhenOneLevel() {
            assertThat(MailboxPath.forUser(USER, "inbox")
                    .getHierarchyLevels(folderDelimiter()))
                    .containsExactly(
                            MailboxPath.forUser(USER, "inbox"));
        }

        @Test
        void getHierarchyLevelsShouldReturnPathWhenEmptyName() {
            assertThat(MailboxPath.forUser(USER, "")
                    .getHierarchyLevels(folderDelimiter()))
                    .containsExactly(
                            MailboxPath.forUser(USER, ""));
        }

        @Test
        void getHierarchyLevelsShouldReturnPathWhenBlankName() {
            assertThat(MailboxPath.forUser(USER, "  ")
                    .getHierarchyLevels(folderDelimiter()))
                    .containsExactly(
                            MailboxPath.forUser(USER, "  "));
        }

        @Test
        void getHierarchyLevelsShouldReturnPathWhenNullName() {
            assertThat(MailboxPath.forUser(USER, null)
                    .getHierarchyLevels(folderDelimiter()))
                    .containsExactly(
                            MailboxPath.forUser(USER, null));
        }

        @Test
        void sanitizeShouldNotThrowOnNullMailboxName() {
            assertThat(MailboxPath.forUser(USER, null)
                    .sanitize(folderDelimiter()))
                    .isEqualTo(
                            MailboxPath.forUser(USER, null));
        }

        @Test
        void sanitizeShouldReturnEmptyWhenEmpty() {
            assertThat(MailboxPath.forUser(USER, "")
                    .sanitize(folderDelimiter()))
                    .isEqualTo(
                            MailboxPath.forUser(USER, ""));
        }

        @Test
        void sanitizeShouldRemoveMaximumOneTrailingDelimiterWhenAlone() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("."))
                    .sanitize(folderDelimiter()))
                    .isEqualTo(
                            MailboxPath.forUser(USER, ""));
        }

        @Test
        void sanitizeShouldPreserveHeadingDelimiter() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter(".a"))
                    .sanitize(folderDelimiter()))
                    .isEqualTo(
                            MailboxPath.forUser(USER, adjustToActiveFolderDelimiter(".a")));
        }

        @Test
        void sanitizeShouldRemoveTrailingDelimiter() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("a."))
                    .sanitize(folderDelimiter()))
                    .isEqualTo(
                            MailboxPath.forUser(USER, "a"));
        }

        @Test
        void sanitizeShouldRemoveMaximumOneTrailingDelimiter() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("a.."))
                    .sanitize(folderDelimiter()))
                    .isEqualTo(
                            MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("a.")));
        }

        @Test
        void sanitizeShouldPreserveRedundantDelimiters() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("a..a"))
                    .sanitize(folderDelimiter()))
                    .isEqualTo(
                            MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("a..a")));
        }

        @Test
        void hasEmptyNameInHierarchyShouldBeFalseIfSingleLevelPath() {
            assertThat(MailboxPath.forUser(USER, "a")
                    .hasEmptyNameInHierarchy(folderDelimiter()))
                    .isFalse();
        }

        @Test
        void hasEmptyNameInHierarchyShouldBeFalseIfNestedLevelWithNonEmptyNames() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("a.b.c"))
                    .hasEmptyNameInHierarchy(folderDelimiter()))
                    .isFalse();
        }

        @Test
        void hasEmptyNameInHierarchyShouldBeTrueIfEmptyPath() {
            assertThat(MailboxPath.forUser(USER, "")
                    .hasEmptyNameInHierarchy(folderDelimiter()))
                    .isTrue();
        }

        @Test
        void hasEmptyNameInHierarchyShouldBeTrueIfBlankPath() {
            assertThat(MailboxPath.forUser(USER, " ")
                    .hasEmptyNameInHierarchy(folderDelimiter()))
                    .isTrue();
        }

        @Test
        void hasEmptyNameInHierarchyShouldBeTrueIfPathWithTwoEmptyNames() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("."))
                    .hasEmptyNameInHierarchy(folderDelimiter()))
                    .isTrue();
        }

        @Test
        void hasEmptyNameInHierarchyShouldBeTrueIfPathWithAnEmptyNameBetweenTwoNames() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("a..b"))
                    .hasEmptyNameInHierarchy(folderDelimiter()))
                    .isTrue();
        }

        @Test
        void hasEmptyNameInHierarchyShouldBeTrueIfPathWithABlankNameBetweenTwoNames() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("a.   .b"))
                    .hasEmptyNameInHierarchy(folderDelimiter()))
                    .isTrue();
        }

        @Test
        void hasEmptyNameInHierarchyShouldBeTrueIfPathWithHeadingEmptyNames() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("..a"))
                    .hasEmptyNameInHierarchy(folderDelimiter()))
                    .isTrue();
        }

        @Test
        void hasEmptyNameInHierarchyShouldBeTrueIfPathWithHeadingBlankName() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("  .a"))
                    .hasEmptyNameInHierarchy(folderDelimiter()))
                    .isTrue();
        }

        @Test
        void hasEmptyNameInHierarchyShouldBeTrueIfPathWithATrailingEmptyName() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("a."))
                    .hasEmptyNameInHierarchy(folderDelimiter()))
                    .isTrue();
        }

        @Test
        void hasEmptyNameInHierarchyShouldBeTrueIfPathWithATrailingBlankName() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("a.  "))
                    .hasEmptyNameInHierarchy(folderDelimiter()))
                    .isTrue();
        }

        @Test
        void hasEmptyNameInHierarchyShouldBeTrueIfPathWithTrailingEmptyNames() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("a.."))
                    .hasEmptyNameInHierarchy(folderDelimiter()))
                    .isTrue();
        }

        @Test
        void hasEmptyNameInHierarchyShouldBeTrueIfPathWithTrailingBlankNames() {
            assertThat(MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("a. .  "))
                    .hasEmptyNameInHierarchy(folderDelimiter()))
                    .isTrue();
        }

        @Test
        void assertAcceptableShouldThrowOnDoubleSeparator() {
            assertThatThrownBy(() -> MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("a..b"))
                    .assertAcceptable(folderDelimiter()))
                    .isInstanceOf(HasEmptyMailboxNameInHierarchyException.class);
        }

        @Test
        void assertAcceptableShouldThrowWhenStartsWithSharp() {
            assertThatThrownBy(() -> MailboxPath.forUser(USER, "#ab")
                    .assertAcceptable(folderDelimiter()))
                    .isInstanceOf(MailboxNameException.class);
        }

        @Test
        void assertAcceptableShouldNotThrowWhenSharpInTheMiddle() {
            assertThatCode(() -> MailboxPath.forUser(USER, "mailbox #17")
                    .assertAcceptable(folderDelimiter()))
                    .doesNotThrowAnyException();
        }

        @Test
        void assertAcceptableShouldThrowOnPercent() {
            assertThatThrownBy(() -> MailboxPath.forUser(USER, "a%b")
                    .assertAcceptable(folderDelimiter()))
                    .isInstanceOf(MailboxNameException.class);
        }

        @Test
        void assertAcceptableShouldThrowOnWildcard() {
            assertThatThrownBy(() -> MailboxPath.forUser(USER, "a*b")
                    .assertAcceptable(folderDelimiter()))
                    .isInstanceOf(MailboxNameException.class);
        }

        @Nested
        class WithMailboxNameValidationRelaxed {
            @BeforeEach
            void setUp() {
                MailboxPath.RELAX_MAILBOX_NAME_VALIDATION = true;
                MailboxPath.INVALID_CHARS = MailboxPath.evaluateInvalidChars();
                MailboxPath.INVALID_CHARS_MATCHER = CharMatcher.anyOf(MailboxPath.INVALID_CHARS);
            }

            @AfterEach
            void tearDown() {
                MailboxPath.RELAX_MAILBOX_NAME_VALIDATION = false;
                MailboxPath.INVALID_CHARS = MailboxPath.evaluateInvalidChars();
                MailboxPath.INVALID_CHARS_MATCHER = CharMatcher.anyOf(MailboxPath.INVALID_CHARS);
            }

            @Test
            void assertAcceptableShouldNotThrowOnPercentWhenRelaxMode() {
                assertThatCode(() -> MailboxPath.forUser(USER, "a%b")
                        .assertAcceptable(folderDelimiter()))
                        .doesNotThrowAnyException();
            }

            @Test
            void assertAcceptableShouldNotThrowOnWildcardWhenRelaxMode() {
                assertThatCode(() -> MailboxPath.forUser(USER, "a*b")
                        .assertAcceptable(folderDelimiter()))
                        .doesNotThrowAnyException();
            }
        }

        @Test
        void assertAcceptableShouldThrowOnTooLongMailboxName() {
            assertThatThrownBy(() -> MailboxPath.forUser(USER, Strings.repeat("a", 201))
                    .assertAcceptable(folderDelimiter()))
                    .isInstanceOf(TooLongMailboxNameException.class);
        }

        @Test
        void assertAcceptableShouldNotThrowOnNotTooLongMailboxName() {
            assertThatCode(() -> MailboxPath.forUser(USER, Strings.repeat("a", 200))
                    .assertAcceptable(folderDelimiter()))
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

        @Test
        void hasParentShouldReturnTrueWhenMailboxHasParent() {
            MailboxPath mailboxPath = MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("inbox.folder.subfolder"));
            assertThat(mailboxPath.hasParent(folderDelimiter())).isTrue();
        }

        @Test
        void hasParentShouldReturnFalseWhenNoParent() {
            MailboxPath mailboxPath = MailboxPath.forUser(USER, "inbox");
            assertThat(mailboxPath.hasParent(folderDelimiter())).isFalse();
        }

        @Test
        void getParentShouldReturnParents() {
            MailboxPath mailboxPath = MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("inbox.folder.subfolder"));
            assertThat(mailboxPath.getParents(folderDelimiter()))
                    .containsExactly(
                            MailboxPath.forUser(USER, "inbox"),
                            MailboxPath.forUser(USER, adjustToActiveFolderDelimiter("inbox.folder")));
        }

        @Test
        void getParentShouldReturnEmptyWhenTopLevelMailbox() {
            MailboxPath mailboxPath = MailboxPath.forUser(USER, "inbox");
            assertThat(mailboxPath.getParents(folderDelimiter()))
                    .isEmpty();
        }
    }
}
