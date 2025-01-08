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

package org.apache.james.mailbox.model.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxFolderDelimiterAwareTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.re2j.Pattern;

import nl.jqno.equalsverifier.EqualsVerifier;

class PrefixedRegexTest {
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
        private static final String PREFIX = "name";
        private static final String EMPTY_PREFIX = "";

        @Test
        public void shouldMatchBeanContract() {
            EqualsVerifier.forClass(PrefixedRegex.class)
                    .withIgnoredFields("pattern")
                    .withPrefabValues(Pattern.class, Pattern.compile("a"), Pattern.compile("b"))
                    .verify();
        }

        @Test
        void slowRegexShouldNotBeConstructedByFuzzing() throws Exception {
            ExecutorService executorService = Executors.newSingleThreadExecutor();

            try {
                executorService.submit(() -> {
                    PrefixedRegex prefixedRegex = new PrefixedRegex("", "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%a", folderDelimiter());

                    prefixedRegex.isExpressionMatch("aa%%%%%%%%%%%%%%%%");
                }).get(30, TimeUnit.SECONDS);
            } finally {
                executorService.shutdownNow();
            }
        }

        @Test
        void isWildShouldReturnTrueWhenOnlyFreeWildcard() {
            PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "*", folderDelimiter());

            boolean actual = prefixedRegex.isWild();

            assertThat(actual).isTrue();
        }

        @Test
        void isWildShouldReturnTrueWhenOnlyLocalWildcard() {
            PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "%", folderDelimiter());

            boolean actual = prefixedRegex.isWild();

            assertThat(actual).isTrue();
        }

        @Test
        void isWildShouldReturnTrueWhenFreeWildcardAtBeginning() {
            PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "*One", folderDelimiter());

            boolean actual = prefixedRegex.isWild();

            assertThat(actual).isTrue();
        }

        @Test
        void isWildShouldReturnTrueWhenLocalWildcardAtBeginning() {
            PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "%One", folderDelimiter());

            boolean actual = prefixedRegex.isWild();

            assertThat(actual).isTrue();
        }

        @Test
        void isWildShouldReturnTrueWhenFreeWildcardInMiddle() {
            PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "A*A", folderDelimiter());

            boolean actual = prefixedRegex.isWild();

            assertThat(actual).isTrue();
        }

        @Test
        void isWildShouldReturnTrueWhenLocalWildcardInMiddle() {
            PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "A%A", folderDelimiter());

            boolean actual = prefixedRegex.isWild();

            assertThat(actual).isTrue();
        }

        @Test
        void isWildShouldReturnTrueWhenFreeWildcardAtEnd() {
            PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "One*", folderDelimiter());

            boolean actual = prefixedRegex.isWild();

            assertThat(actual).isTrue();
        }

        @Test
        void isWildShouldReturnTrueWhenLocalWildcardAtEnd() {
            PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "One%", folderDelimiter());

            boolean actual = prefixedRegex.isWild();

            assertThat(actual).isTrue();
        }

        @Test
        void isWildShouldReturnFalseWhenEmptyExpression() {
            PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "", folderDelimiter());

            boolean actual = prefixedRegex.isWild();

            assertThat(actual).isFalse();
        }

        @Test
        void isWildShouldReturnFalseWhenNullExpression() {
            PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, null, folderDelimiter());

            boolean actual = prefixedRegex.isWild();

            assertThat(actual).isFalse();
        }

        @Test
        void isWildShouldReturnFalseWhenNoWildcard() {
            PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "ONE", folderDelimiter());

            boolean actual = prefixedRegex.isWild();

            assertThat(actual).isFalse();
        }

        @Test
        void getCombinedNameShouldWork() {
            PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "mailbox", folderDelimiter());

            String actual = prefixedRegex.getCombinedName();

            assertThat(actual).isEqualTo(adjustToActiveFolderDelimiter("name.mailbox"));
        }

        @Test
        void getCombinedNameShouldWorkWhenEmptyExpression() {
            PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "", folderDelimiter());

            String actual = prefixedRegex.getCombinedName();

            assertThat(actual).isEqualTo("name");
        }

        @Test
        void getCombinedNameShouldReturnEmptyStringWhenNullMailboxPathAndExpression() {
            String prefix = null;
            String regex = null;
            PrefixedRegex prefixedRegex = new PrefixedRegex(prefix, regex, folderDelimiter());

            String actual = prefixedRegex.getCombinedName();

            assertThat(actual).isEmpty();
        }

        @Test
        void getCombinedNameShouldIgnoreDelimiterWhenPresentAtBeginningOfExpression() {
            PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, adjustToActiveFolderDelimiter(".mailbox"), folderDelimiter());

            String actual = prefixedRegex.getCombinedName();

            assertThat(actual).isEqualTo(adjustToActiveFolderDelimiter("name.mailbox"));
        }

        @Test
        void getCombinedNameShouldIgnoreDelimiterWhenPresentAtEndOfMailboxName() {
            PrefixedRegex prefixedRegex = new PrefixedRegex(
                    PREFIX + adjustToActiveFolderDelimiter("."),
                    adjustToActiveFolderDelimiter(".mailbox"),
                    folderDelimiter());

            String actual = prefixedRegex.getCombinedName();

            assertThat(actual).isEqualTo(adjustToActiveFolderDelimiter("name.mailbox"));
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenNullExpression() {
            PrefixedRegex testee = new PrefixedRegex(PREFIX, null, folderDelimiter());

            boolean actual = testee.isExpressionMatch("folder");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenMatching() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("mailbox");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldReturnTrueWhenNameBeginsWithDelimiter() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter(".mailbox"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenNameEndsWithDelimiter() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("mailbox."));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchFolderWhenNoMatching() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("sub");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchFolderWithExpandedEndName() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("mailbox123");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchSubFolder() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("mailbox.123"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldReturnTrueWhenEmptyNameAndExpression() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "", folderDelimiter());

            boolean actual = testee.isExpressionMatch("");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenEmptyExpressionAndNameBeginsWithDelimiter() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter(".123"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchFolderWhenEmptyExpression() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "", folderDelimiter());

            boolean actual = testee.isExpressionMatch("folder");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldReturnTrueWhenEmptyNameAndOnlyLocalWildcard() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%", folderDelimiter());

            boolean actual = testee.isExpressionMatch("");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldReturnTrueWhenOnlyLocalWildcard() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%", folderDelimiter());

            boolean actual = testee.isExpressionMatch("folder");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldNotMatchSubFolderWhenOnlyLocalWildcard() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("mailbox.sub"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldReturnTrueWhenEmptyNameAndOnlyFreeWildcard() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*", folderDelimiter());

            boolean actual = testee.isExpressionMatch("");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenOnlyFreeWildcard() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*", folderDelimiter());

            boolean actual = testee.isExpressionMatch("sub");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchSubFolderWhenOnlyFreeWildcard() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("mailbox.sub"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenEmptyNameAndLocalWildcardAtEnd() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox%", folderDelimiter());

            boolean actual = testee.isExpressionMatch("");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenLocalWildcardAtEndAndNoMatching() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox%", folderDelimiter());

            boolean actual = testee.isExpressionMatch("sub");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenLocalWildcardAtEndNotUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox%", folderDelimiter());

            boolean actual = testee.isExpressionMatch("mailbox");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldReturnTrueWhenLocalWildcardAtEndUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox%", folderDelimiter());

            boolean actual = testee.isExpressionMatch("mailboxsub");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldNotMatchSubFolderWhenLocalWildcardAtEnd() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox%", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("mailbox.sub"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenEmptyNameAndLocalWildcardAtBeginning() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchFolderWhenLocalWildcardAtBeginningAndNoMatching() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("sub");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenLocalWildcardAtBeginningNotUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("mailbox");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenLocalWildcardAtBeginningUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("submailbox");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldNotMatchSubFolderWhenLocalWildcardAtBeginning() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.mailbox"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenLocalWildcardAtBeginning() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.mailbox.sub"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenEmptyNameAndLocalWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenLocalWildcardInMiddleAndMissingEndName() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("sub");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenLocalWildcardInMiddleAndMatching() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("submailbox");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenLocalWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("sub123mailbox");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldNotMatchSubFolderWhenLocalWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.mailbox"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchSubFolderWhenLocalWildcardInMiddleAndExpandedMiddleName() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.123mailbox"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenLocalWildcardInMiddleAndMissingBeginningName() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("mailbox");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenLocalWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("subw.hat.eve.rmailbox"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchSubFolderWhenFreeWildcardAtEnd() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox*", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("mailbox.sub"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenEmptyNameAndFreeWildcardAtEnd() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox*", folderDelimiter());

            boolean actual = testee.isExpressionMatch("");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchFolderWhenFreeWildcardAtEndAndNoMatching() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox*", folderDelimiter());

            boolean actual = testee.isExpressionMatch("sub");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenFreeWildcardAtEndNotUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox*", folderDelimiter());

            boolean actual = testee.isExpressionMatch("mailbox");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenFreeWildcardAtEndUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox*", folderDelimiter());

            boolean actual = testee.isExpressionMatch("mailbox123");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenEmptyNameAndFreeWildcardAtBeginning() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchFolderWhenFreeWildcardAtBeginningAndNoMatching() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("sub");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenFreeWildcardAtBeginningNotUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("mailbox");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenFreeWildcardAtBeginningUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("submailbox");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchSubFolderWhenFreeWildcardAtBeginning() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.mailbox"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenEmptyNameAndFreeWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchFolderWhenFreeWildcardInMiddleAndMissingEndName() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("sub");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenFreeWildcardInMiddleNotUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("submailbox");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchSubFolderWhenFreeWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.mailbox"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenFreeWildcardInMiddleNotUsedAndMissingBeginningName() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("mailbox");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchDeeplyNestedFolderWhenFreeWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("subw.hat.eve.rmailbox"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenEmptyNameAndDoubleFreeWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub**mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenDoubleFreeWildcardInMiddleAndMissingEndName() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub**mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("sub");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldReturnTrueWhenDoubleFreeWildcardInMiddleNotUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub**mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("submailbox");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchSubFolderWhenDoubleFreeWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub**mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.mailbox"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenDoubleFreeWildcardInMiddleAndMissingBeginningName() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub**mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("mailbox");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchDeeplyNestedFolderWhenDoubleFreeWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub**mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("subw.hat.eve.rmailbox"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenEmptyNameAndFreeLocalWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenFreeLocalWildcardInMiddleAndMissingEndName() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("sub");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenFreeLocalWildcardInMiddleNotUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("submailbox");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchSubFolderWhenFreeLocalWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.mailbox"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenFreeLocalWildcardInMiddleAndMissingBeginningName() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("mailbox");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchDeeplyNestedFolderWhenFreeLocalWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*%mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("subw.hat.eve.rmailbox"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldReturnFalseWhenEmptyNameAndLocalFreeWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchFolderWhenLocalFreeWildcardInMiddleAndMissingEndName() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("sub");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenLocalFreewildcardInMiddleNotUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("submailbox");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchSubFolderWhenLocalFreeWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.mailbox"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldNotMatchFolderWhenLocalFreeWildcardInMiddleAndMissingBeginningName() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch("mailbox");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchDeeplyNestedFolderWhenLocalFreeWildcardInMiddle() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%*mailbox", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("subw.hat.eve.rmailbox"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenMultipleFreeWildcards() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox*sub**", folderDelimiter());

            boolean actual = testee.isExpressionMatch("submailboxsub");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchDeeplyNestedFolderWhenMultipleFreeWildcardsNotUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox*sub**", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.mailbox.sub"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchDeeplyNestedFolderWhenMultipleFreeWildcardsUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox*sub**", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("subtosh.boshmailboxtosh.boshsubboshtosh"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenMultipleFreeWildcardsAndMissingMiddleName() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox*sub**", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.a.sub"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenMultipleFreeWildcardsAndMissingEndName() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox*sub**", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.a.submailbox.u"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenMultipleFreeWildcardsAndMissingBeginningdName() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox*sub**", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("utosh.boshmailboxtosh.boshsubasubboshtoshmailboxu"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenMixedLocalFreeWildcardsNotUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox*sub", folderDelimiter());

            boolean actual = testee.isExpressionMatch("submailboxsub");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldNotMatchSubFolderWhenMixedLocalFreeWildcards() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox*sub", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.mailboxsub"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenMixedFreeLocalWildcardsNotUsed() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", folderDelimiter());

            boolean actual = testee.isExpressionMatch("submailboxsub");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchSubFolderWhenMixedFreeLocalWildcards() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.mailboxsub"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldNotMatchSubFolderWhenMixedFreeLocalWildcards() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("submailbox.sub"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchFolderWhenMixedFreeLocalWildcards() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", folderDelimiter());

            boolean actual = testee.isExpressionMatch("submailboxwhateversub");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldNotMatchSubFolderEndingWithDelimiterWhenMixedFreeLocalWildcards() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("submailboxsub.Whatever."));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenMixedFreeLocalWildcards() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.mailboxsub.sub"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchSubFoldeWhenMixedFreeLocalWildcards() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.mailboxsub"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchDeeplyNestedFoldeWhenMixedFreeLocalWildcards() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("sub.whatever.mailbox123sub"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldNotMatchFolderWhenTwoLocalPathDelimitedWildcards() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, adjustToActiveFolderDelimiter("%.%"), folderDelimiter());

            boolean actual = testee.isExpressionMatch("mailbox");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenTwoLocalPathDelimitedWildcards() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, adjustToActiveFolderDelimiter("%.%"), folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("mailbox.sub.sub"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldMatchSubFolderWhenTwoLocalPathDelimitedWildcards() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, adjustToActiveFolderDelimiter("%.%"), folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("mailbox.sub"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldMatchSubFolderWhenFreeWildcardAndPathDelimiterAtBeginning() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, adjustToActiveFolderDelimiter("*.test"), folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("blah.test"));

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldNotMatchSubFolderWhenWhenFreeWildcardAndPathDelimiterAtBeginning() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, adjustToActiveFolderDelimiter("*.test"), folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("blah.test3"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenFreeWildcardAndPathDelimiterAtBeginning() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, adjustToActiveFolderDelimiter("*.test"), folderDelimiter());

            boolean actual = testee.isExpressionMatch(adjustToActiveFolderDelimiter("blah.test.go"));

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldIgnoreRegexInjection() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "folder^$!)(%3", folderDelimiter());

            boolean actual = testee.isExpressionMatch("folder^$!)(123");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldIgnoreRegexInjectionWhenUsingEndOfQuoteAndNoMatching() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "\\Efo.", folderDelimiter());

            boolean actual = testee.isExpressionMatch("\\Efol");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldIgnoreRegexInjectionWhenUsingEndOfQuoteAndMatching() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "\\Efo.", folderDelimiter());

            boolean actual = testee.isExpressionMatch("\\Efo.");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldIgnoreRegexInjectionWhenUsingBeginOfQuoteAndNoMatching() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "\\Qfo?", folderDelimiter());

            boolean actual = testee.isExpressionMatch("\\Qfol");

            assertThat(actual).isFalse();
        }

        @Test
        void isExpressionMatchShouldIgnoreRegexInjectionWhenUsingBeginOfQuoteAndMatching() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "\\Qfo?", folderDelimiter());

            boolean actual = testee.isExpressionMatch("\\Qfo?");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldNotEscapeFreeWildcard() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "folder\\*", folderDelimiter());

            boolean actual = testee.isExpressionMatch("folder\\123");

            assertThat(actual).isTrue();
        }

        @Test
        void isExpressionMatchShouldNotEscapeLocalWildcard() {
            PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "folder\\%", folderDelimiter());

            boolean actual = testee.isExpressionMatch("folder\\123");

            assertThat(actual).isTrue();
        }
    }
}