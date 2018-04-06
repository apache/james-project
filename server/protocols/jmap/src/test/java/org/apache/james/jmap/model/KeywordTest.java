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

package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;

import javax.mail.Flags;

import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import nl.jqno.equalsverifier.EqualsVerifier;

public class KeywordTest {
    private static final int FLAG_NAME_MAX_LENTH = 255;
    private static final String ANY_KEYWORD = "AnyKeyword";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldRespectBeanContract() {
        EqualsVerifier.forClass(Keyword.class).verify();
    }

    @Test
    public void keywordShouldThrowWhenFlagNameLengthLessThanMinLength() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        new Keyword("");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameLengthMoreThanMaxLength() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        new Keyword(StringUtils.repeat("a", FLAG_NAME_MAX_LENTH + 1));
    }

    @Test
    public void keywordShouldCreateNewOneWhenFlagNameLengthEqualsMaxLength() throws Exception {
        String maxLengthFlagName = StringUtils.repeat("a", FLAG_NAME_MAX_LENTH);
        Keyword keyword = new Keyword(maxLengthFlagName);

        assertThat(keyword.getFlagName()).isEqualTo(maxLengthFlagName);
    }

    @Test
    public void keywordShouldCreateNewOneWhenFlagNameLengthEqualsMinLength() throws Exception {
        String minLengthFlagName = "a";
        Keyword keyword = new Keyword(minLengthFlagName);

        assertThat(keyword.getFlagName()).isEqualTo(minLengthFlagName);
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsPercentageCharacter() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        new Keyword("a%");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsLeftBracket() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        new Keyword("a[");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsRightBracket() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        new Keyword("a]");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsLeftBrace() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        new Keyword("a{");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsSlash() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        new Keyword("a\\");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsStar() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        new Keyword("a*");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsQuote() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        new Keyword("a\"");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsOpeningParenthesis() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        new Keyword("a(");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsClosingParenthesis() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        new Keyword("a)");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsSpaceCharacter() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        new Keyword("a b");
    }

    @Test
    public void isNotNonExposedImapKeywordShouldReturnFalseWhenDeleted() throws Exception {
        assertThat(Keyword.DELETED.isExposedImapKeyword()).isFalse();
    }

    @Test
    public void isNotNonExposedImapKeywordShouldReturnFalseWhenRecent() throws Exception {
        assertThat(Keyword.RECENT.isExposedImapKeyword()).isFalse();
    }

    @Test
    public void isNotNonExposedImapKeywordShouldReturnTrueWhenOtherSystemFlag() throws Exception {
        assertThat(Keyword.DRAFT.isExposedImapKeyword()).isTrue();
    }

    @Test
    public void isNotNonExposedImapKeywordShouldReturnTrueWhenAnyUserFlag() throws Exception {
        Keyword keyword = new Keyword(ANY_KEYWORD);
        assertThat(keyword.isExposedImapKeyword()).isTrue();
    }

    @Test
    public void isDraftShouldReturnTrueWhenDraft() throws Exception {
        assertThat(Keyword.DRAFT.isDraft()).isTrue();
    }

    @Test
    public void isDraftShouldReturnFalseWhenNonDraft() throws Exception {
        assertThat(Keyword.DELETED.isDraft()).isFalse();
    }

    @Test
    public void asSystemFlagShouldReturnSystemFlag() throws Exception {
        assertThat(new Keyword("$Draft").asSystemFlag())
            .isEqualTo(Optional.of(Flags.Flag.DRAFT));
    }

    @Test
    public void asSystemFlagShouldReturnEmptyWhenNonSystemFlag() throws Exception {
        assertThat(new Keyword(ANY_KEYWORD).asSystemFlag().isPresent())
            .isFalse();
    }

    @Test
    public void asFlagsShouldReturnFlagsWhenSystemFlag() throws Exception {
        assertThat(Keyword.DELETED.asFlags())
            .isEqualTo(new Flags(Flags.Flag.DELETED));
    }

    @Test
    public void asFlagsShouldReturnFlagsWhenUserFlag() throws Exception {
        Keyword keyword = new Keyword(ANY_KEYWORD);
        assertThat(keyword.asFlags())
            .isEqualTo(new Flags(ANY_KEYWORD));
    }

    @Test
    public void asFlagsShouldReturnFlagsWhenUserFlagContainsUnderscore() throws Exception {
        String userFlag = "$has_cal";
        Keyword keyword = new Keyword(userFlag);
        assertThat(keyword.asFlags())
            .isEqualTo(new Flags(userFlag));
    }

    @Test
    public void hyphenMinusShouldBeAllowedInKeyword() {
        String userFlag = "aa-bb";

        assertThatCode(() -> new Keyword(userFlag))
            .doesNotThrowAnyException();
    }
}