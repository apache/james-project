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

package org.apache.james.jmap.draft.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;

import jakarta.mail.Flags;

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
    public void keywordShouldThrowWhenFlagNameLengthLessThanMinLength() {
        expectedException.expect(IllegalArgumentException.class);
        Keyword.of("");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameLengthMoreThanMaxLength() {
        expectedException.expect(IllegalArgumentException.class);
        Keyword.of(StringUtils.repeat("a", FLAG_NAME_MAX_LENTH + 1));
    }

    @Test
    public void keywordShouldCreateNewOneWhenFlagNameLengthEqualsMaxLength() {
        String maxLengthFlagName = StringUtils.repeat("a", FLAG_NAME_MAX_LENTH);
        Keyword keyword = Keyword.of(maxLengthFlagName);

        assertThat(keyword.getFlagName()).isEqualTo(maxLengthFlagName);
    }

    @Test
    public void keywordShouldCreateNewOneWhenFlagNameLengthEqualsMinLength() {
        String minLengthFlagName = "a";
        Keyword keyword = Keyword.of(minLengthFlagName);

        assertThat(keyword.getFlagName()).isEqualTo(minLengthFlagName);
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsPercentageCharacter() {
        expectedException.expect(IllegalArgumentException.class);
        Keyword.of("a%");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsLeftBracket() {
        expectedException.expect(IllegalArgumentException.class);
        Keyword.of("a[");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsRightBracket() {
        expectedException.expect(IllegalArgumentException.class);
        Keyword.of("a]");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsLeftBrace() {
        expectedException.expect(IllegalArgumentException.class);
        Keyword.of("a{");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsSlash() {
        expectedException.expect(IllegalArgumentException.class);
        Keyword.of("a\\");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsStar() {
        expectedException.expect(IllegalArgumentException.class);
        Keyword.of("a*");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsQuote() {
        expectedException.expect(IllegalArgumentException.class);
        Keyword.of("a\"");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsOpeningParenthesis() {
        expectedException.expect(IllegalArgumentException.class);
        Keyword.of("a(");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsClosingParenthesis() {
        expectedException.expect(IllegalArgumentException.class);
        Keyword.of("a)");
    }

    @Test
    public void keywordShouldThrowWhenFlagNameContainsSpaceCharacter() {
        expectedException.expect(IllegalArgumentException.class);
        Keyword.of("a b");
    }

    @Test
    public void isNotNonExposedImapKeywordShouldReturnFalseWhenDeleted() {
        assertThat(Keyword.DELETED.isExposedImapKeyword()).isFalse();
    }

    @Test
    public void isNotNonExposedImapKeywordShouldReturnFalseWhenRecent() {
        assertThat(Keyword.RECENT.isExposedImapKeyword()).isFalse();
    }

    @Test
    public void isNotNonExposedImapKeywordShouldReturnTrueWhenOtherSystemFlag() {
        assertThat(Keyword.DRAFT.isExposedImapKeyword()).isTrue();
    }

    @Test
    public void isNotNonExposedImapKeywordShouldReturnTrueWhenAnyUserFlag() {
        Keyword keyword = Keyword.of(ANY_KEYWORD);
        assertThat(keyword.isExposedImapKeyword()).isTrue();
    }

    @Test
    public void isDraftShouldReturnTrueWhenDraft() {
        assertThat(Keyword.DRAFT.isDraft()).isTrue();
    }

    @Test
    public void isDraftShouldReturnFalseWhenNonDraft() {
        assertThat(Keyword.DELETED.isDraft()).isFalse();
    }

    @Test
    public void asSystemFlagShouldReturnSystemFlag() {
        assertThat(Keyword.of("$Draft").asSystemFlag())
            .isEqualTo(Optional.of(Flags.Flag.DRAFT));
    }

    @Test
    public void asSystemFlagShouldReturnEmptyWhenNonSystemFlag() {
        assertThat(Keyword.of(ANY_KEYWORD).asSystemFlag().isPresent())
            .isFalse();
    }

    @Test
    public void asFlagsShouldReturnFlagsWhenSystemFlag() {
        assertThat(Keyword.DELETED.asFlags())
            .isEqualTo(new Flags(Flags.Flag.DELETED));
    }

    @Test
    public void asFlagsShouldReturnFlagsWhenUserFlag() {
        Keyword keyword = Keyword.of(ANY_KEYWORD);
        assertThat(keyword.asFlags())
            .isEqualTo(new Flags(ANY_KEYWORD));
    }

    @Test
    public void asFlagsShouldReturnFlagsWhenUserFlagContainsUnderscore() {
        String userFlag = "$has_cal";
        Keyword keyword = Keyword.of(userFlag);
        assertThat(keyword.asFlags())
            .isEqualTo(new Flags(userFlag));
    }

    @Test
    public void hyphenMinusShouldBeAllowedInKeyword() {
        String userFlag = "aa-bb";

        assertThatCode(() -> Keyword.of(userFlag))
            .doesNotThrowAnyException();
    }
}