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

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class PrefixedRegexTest {
    private static final char PATH_DELIMITER = '.';
    private static final String PREFIX = "name";
    private static final String EMPTY_PREFIX = "";

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(PrefixedRegex.class)
            .withIgnoredFields("pattern")
            .verify();
    }

    @Test
    public void isWildShouldReturnTrueWhenOnlyFreeWildcard() throws Exception {
        PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "*", PATH_DELIMITER);

        boolean actual = prefixedRegex.isWild();

        assertThat(actual).isTrue();
    }

    @Test
    public void isWildShouldReturnTrueWhenOnlyLocalWildcard() throws Exception {
        PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "%", PATH_DELIMITER);

        boolean actual = prefixedRegex.isWild();

        assertThat(actual).isTrue();
    }

    @Test
    public void isWildShouldReturnTrueWhenFreeWildcardAtBeginning() throws Exception {
        PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "*One", PATH_DELIMITER);

        boolean actual = prefixedRegex.isWild();

        assertThat(actual).isTrue();
    }

    @Test
    public void isWildShouldReturnTrueWhenLocalWildcardAtBeginning() throws Exception {
        PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "%One", PATH_DELIMITER);

        boolean actual = prefixedRegex.isWild();

        assertThat(actual).isTrue();
    }

    @Test
    public void isWildShouldReturnTrueWhenFreeWildcardInMiddle() throws Exception {
        PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "A*A", PATH_DELIMITER);

        boolean actual = prefixedRegex.isWild();

        assertThat(actual).isTrue();
    }

    @Test
    public void isWildShouldReturnTrueWhenLocalWildcardInMiddle() throws Exception {
        PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "A%A", PATH_DELIMITER);

        boolean actual = prefixedRegex.isWild();

        assertThat(actual).isTrue();
    }

    @Test
    public void isWildShouldReturnTrueWhenFreeWildcardAtEnd() throws Exception {
        PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "One*", PATH_DELIMITER);

        boolean actual = prefixedRegex.isWild();

        assertThat(actual).isTrue();
    }

    @Test
    public void isWildShouldReturnTrueWhenLocalWildcardAtEnd() throws Exception {
        PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "One%", PATH_DELIMITER);

        boolean actual = prefixedRegex.isWild();

        assertThat(actual).isTrue();
    }

    @Test
    public void isWildShouldReturnFalseWhenEmptyExpression() throws Exception {
        PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "", PATH_DELIMITER);

        boolean actual = prefixedRegex.isWild();

        assertThat(actual).isFalse();
    }

    @Test
    public void isWildShouldReturnFalseWhenNullExpression() throws Exception {
        PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, null, PATH_DELIMITER);

        boolean actual = prefixedRegex.isWild();

        assertThat(actual).isFalse();
    }

    @Test
    public void isWildShouldReturnFalseWhenNoWildcard() throws Exception {
        PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "ONE", PATH_DELIMITER);

        boolean actual = prefixedRegex.isWild();

        assertThat(actual).isFalse();
    }

    @Test
    public void getCombinedNameShouldWork() throws Exception {
        PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "mailbox", PATH_DELIMITER);

        String actual = prefixedRegex.getCombinedName();

        assertThat(actual).isEqualTo("name.mailbox");
    }

    @Test
    public void getCombinedNameShouldWorkWhenEmptyExpression() throws Exception {
        PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, "", PATH_DELIMITER);

        String actual = prefixedRegex.getCombinedName();

        assertThat(actual).isEqualTo("name");
    }

    @Test
    public void getCombinedNameShouldReturnEmptyStringWhenNullMailboxPathAndExpression() throws Exception {
        String prefix = null;
        String regex = null;
        PrefixedRegex prefixedRegex = new PrefixedRegex(prefix, regex, PATH_DELIMITER);

        String actual = prefixedRegex.getCombinedName();

        assertThat(actual).isEmpty();
    }

    @Test
    public void getCombinedNameShouldIgnoreDelimiterWhenPresentAtBeginningOfExpression() throws Exception {
        PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX, ".mailbox", PATH_DELIMITER);

        String actual = prefixedRegex.getCombinedName();

        assertThat(actual).isEqualTo("name.mailbox");
    }

    @Test
    public void getCombinedNameShouldIgnoreDelimiterWhenPresentAtEndOfMailboxName() throws Exception {
        PrefixedRegex prefixedRegex = new PrefixedRegex(PREFIX + ".", ".mailbox", PATH_DELIMITER);

        String actual = prefixedRegex.getCombinedName();

        assertThat(actual).isEqualTo("name.mailbox");
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenNullExpression() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(PREFIX, null, PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("folder");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenMatching() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenNameBeginsWithDelimiter() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch(".mailbox");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenNameEndsWithDelimiter() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox.");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchFolderWhenNoMatching() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchFolderWithExpandedEndName() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox123");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchSubFolder() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox.123");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldReturnTrueWhenEmptyNameAndExpression() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenEmptyExpressionAndNameBeginsWithDelimiter() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch(".123");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchFolderWhenEmptyExpression() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("folder");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldReturnTrueWhenEmptyNameAndOnlyLocalWildcard() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldReturnTrueWhenOnlyLocalWildcard() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("folder");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldNotMatchSubFolderWhenOnlyLocalWildcard() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox.sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldReturnTrueWhenEmptyNameAndOnlyFreeWildcard() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenOnlyFreeWildcard() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchSubFolderWhenOnlyFreeWildcard() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox.sub");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenEmptyNameAndLocalWildcardAtEnd() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox%", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenLocalWildcardAtEndAndNoMatching() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox%", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenLocalWildcardAtEndNotUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox%", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldReturnTrueWhenLocalWildcardAtEndUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox%", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailboxsub");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldNotMatchSubFolderWhenLocalWildcardAtEnd() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox%", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox.sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenEmptyNameAndLocalWildcardAtBeginning() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchFolderWhenLocalWildcardAtBeginningAndNoMatching() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenLocalWildcardAtBeginningNotUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenLocalWildcardAtBeginningUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("submailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldNotMatchSubFolderWhenLocalWildcardAtBeginning() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.mailbox");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenLocalWildcardAtBeginning() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.mailbox.sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenEmptyNameAndLocalWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenLocalWildcardInMiddleAndMissingEndName() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenLocalWildcardInMiddleAndMatching() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("submailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenLocalWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub123mailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldNotMatchSubFolderWhenLocalWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.mailbox");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchSubFolderWhenLocalWildcardInMiddleAndExpandedMiddleName() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.123mailbox");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenLocalWildcardInMiddleAndMissingBeginningName() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenLocalWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("subw.hat.eve.rmailbox");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchSubFolderWhenFreeWildcardAtEnd() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox*", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox.sub");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenEmptyNameAndFreeWildcardAtEnd() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox*", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchFolderWhenFreeWildcardAtEndAndNoMatching() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox*", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenFreeWildcardAtEndNotUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox*", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenFreeWildcardAtEndUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "mailbox*", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox123");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenEmptyNameAndFreeWildcardAtBeginning() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchFolderWhenFreeWildcardAtBeginningAndNoMatching() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenFreeWildcardAtBeginningNotUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenFreeWildcardAtBeginningUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("submailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchSubFolderWhenFreeWildcardAtBeginning() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.mailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenEmptyNameAndFreeWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchFolderWhenFreeWildcardInMiddleAndMissingEndName() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenFreeWildcardInMiddleNotUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("submailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchSubFolderWhenFreeWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.mailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenFreeWildcardInMiddleNotUsedAndMissingBeginningName() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchDeeplyNestedFolderWhenFreeWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("subw.hat.eve.rmailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenEmptyNameAndDoubleFreeWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub**mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenDoubleFreeWildcardInMiddleAndMissingEndName() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub**mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldReturnTrueWhenDoubleFreeWildcardInMiddleNotUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub**mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("submailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchSubFolderWhenDoubleFreeWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub**mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.mailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenDoubleFreeWildcardInMiddleAndMissingBeginningName() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub**mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchDeeplyNestedFolderWhenDoubleFreeWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub**mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("subw.hat.eve.rmailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenEmptyNameAndFreeLocalWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenFreeLocalWildcardInMiddleAndMissingEndName() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenFreeLocalWildcardInMiddleNotUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("submailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchSubFolderWhenFreeLocalWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.mailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenFreeLocalWildcardInMiddleAndMissingBeginningName() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchDeeplyNestedFolderWhenFreeLocalWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*%mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("subw.hat.eve.rmailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenEmptyNameAndLocalFreeWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchFolderWhenLocalFreeWildcardInMiddleAndMissingEndName() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenLocalFreewildcardInMiddleNotUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("submailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchSubFolderWhenLocalFreeWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.mailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldNotMatchFolderWhenLocalFreeWildcardInMiddleAndMissingBeginningName() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchDeeplyNestedFolderWhenLocalFreeWildcardInMiddle() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%*mailbox", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("subw.hat.eve.rmailbox");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenMultipleFreeWildcards() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox*sub**", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("submailboxsub");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchDeeplyNestedFolderWhenMultipleFreeWildcardsNotUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox*sub**", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.mailbox.sub");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchDeeplyNestedFolderWhenMultipleFreeWildcardsUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox*sub**", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("subtosh.boshmailboxtosh.boshsubboshtosh");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenMultipleFreeWildcardsAndMissingMiddleName() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox*sub**", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.a.sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenMultipleFreeWildcardsAndMissingEndName() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox*sub**", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.a.submailbox.u");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenMultipleFreeWildcardsAndMissingBeginningdName() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox*sub**", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("utosh.boshmailboxtosh.boshsubasubboshtoshmailboxu");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenMixedLocalFreeWildcardsNotUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox*sub", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("submailboxsub");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldNotMatchSubFolderWhenMixedLocalFreeWildcards() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub%mailbox*sub", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.mailboxsub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenMixedFreeLocalWildcardsNotUsed() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("submailboxsub");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchSubFolderWhenMixedFreeLocalWildcards() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.mailboxsub");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldNotMatchSubFolderWhenMixedFreeLocalWildcards() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("submailbox.sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchFolderWhenMixedFreeLocalWildcards() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("submailboxwhateversub");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldNotMatchSubFolderEndingWithDelimiterWhenMixedFreeLocalWildcards() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("submailboxsub.Whatever.");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenMixedFreeLocalWildcards() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.mailboxsub.sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchSubFoldeWhenMixedFreeLocalWildcards() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.mailboxsub");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchDeeplyNestedFoldeWhenMixedFreeLocalWildcards() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "sub*mailbox%sub", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("sub.whatever.mailbox123sub");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldNotMatchFolderWhenTwoLocalPathDelimitedWildcards() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%.%", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenTwoLocalPathDelimitedWildcards() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%.%", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox.sub.sub");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldMatchSubFolderWhenTwoLocalPathDelimitedWildcards() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "%.%", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("mailbox.sub");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldMatchSubFolderWhenFreeWildcardAndPathDelimiterAtBeginning() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*.test", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("blah.test");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldNotMatchSubFolderWhenWhenFreeWildcardAndPathDelimiterAtBeginning() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*.test", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("blah.test3");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldNotMatchDeeplyNestedFolderWhenFreeWildcardAndPathDelimiterAtBeginning() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "*.test", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("blah.test.go");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldIgnoreRegexInjection() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "folder^$!)(%3", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("folder^$!)(123");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldIgnoreRegexInjectionWhenUsingEndOfQuoteAndNoMatching() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "\\Efo.", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("\\Efol");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldIgnoreRegexInjectionWhenUsingEndOfQuoteAndMatching() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "\\Efo.", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("\\Efo.");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldIgnoreRegexInjectionWhenUsingBeginOfQuoteAndNoMatching() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "\\Qfo?", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("\\Qfol");

        assertThat(actual).isFalse();
    }

    @Test
    public void isExpressionMatchShouldIgnoreRegexInjectionWhenUsingBeginOfQuoteAndMatching() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "\\Qfo?", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("\\Qfo?");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldNotEscapeFreeWildcard() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "folder\\*", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("folder\\123");

        assertThat(actual).isTrue();
    }

    @Test
    public void isExpressionMatchShouldNotEscapeLocalWildcard() throws Exception {
        PrefixedRegex testee = new PrefixedRegex(EMPTY_PREFIX, "folder\\%", PATH_DELIMITER);

        boolean actual = testee.isExpressionMatch("folder\\123");

        assertThat(actual).isTrue();
    }
}