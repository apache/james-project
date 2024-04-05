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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.james.jmap.draft.methods.ValidationResult;
import org.apache.james.jmap.draft.model.CreationMessage.DraftEmailer;
import org.apache.james.jmap.model.Keyword;
import org.apache.james.jmap.model.MessageProperties.MessageProperty;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class CreationMessageTest {

    private CreationMessage.Builder testedBuilder;

    @Before
    public void setUp() {
        testedBuilder = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("ba9-0f-dead-beef"))
                .headers(ImmutableMap.of());
    }

    @Test
    public void buildShouldThrowWhenBothMapAndOldKeyword() {
        assertThatThrownBy(() -> CreationMessage.builder()
                .mailboxIds(ImmutableList.of("ba9-0f-dead-beef"))
                .headers(ImmutableMap.of())
                .keywords(ImmutableMap.of("$Draft", true))
                .isAnswered(Optional.of(true))
                .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Does not support keyword and is* at the same time");
    }

    @Test
    public void validateShouldReturnErrorWhenFromIsMissing() {
       testedBuilder = testedBuilder
               .subject("anything");

        CreationMessage sut = testedBuilder.build();

        assertThat(sut.validate()).contains(ValidationResult.builder()
                .message("'from' address is mandatory")
                .property(MessageProperty.from.asFieldName())
                .build()
        );
    }

    @Test
    public void validateShouldReturnErrorWhenFromIsInvalid() {
        testedBuilder = testedBuilder
                .subject("anything");

        CreationMessage sut = testedBuilder.from(DraftEmailer.builder().name("bob").email("bob@domain.com@example.com").build()).build();

        assertThat(sut.validate()).contains(ValidationResult.builder()
                .message("'from' address is mandatory")
                .property(MessageProperty.from.asFieldName())
                .build()
        );
    }

    @Test
    public void validateShouldReturnErrorWhenNoRecipientSet() {
        testedBuilder = testedBuilder
                .subject("anything");

        CreationMessage  sut = testedBuilder
                .from(DraftEmailer.builder().name("bob").email("bob@example.com").build()).build();

        assertThat(sut.validate()).extracting(ValidationResult::getErrorMessage).contains("no recipient address set");
    }

    @Test
    public void validateShouldReturnErrorWhenNoValidRecipientSet() {
        testedBuilder = testedBuilder
                .subject("anything");

        CreationMessage sut = testedBuilder
                .from(DraftEmailer.builder().name("bob").email("bob@example.com").build())
                .to(ImmutableList.of(DraftEmailer.builder().name("riri").email("riri@acme.com@example.com").build()))
                .cc(ImmutableList.of(DraftEmailer.builder().name("fifi").email("fifi@acme.com@example.com").build()))
                .bcc(ImmutableList.of(DraftEmailer.builder().name("loulou").email("loulou@acme.com@example.com").build()))
                .build();

        assertThat(sut.validate()).extracting(ValidationResult::getErrorMessage).contains("no recipient address set");
    }

    @Test
    public void validateShouldReturnEmptyListWhenNoErrors() {
        testedBuilder = testedBuilder
                .subject("anything");

        CreationMessage sut = testedBuilder
                .from(DraftEmailer.builder().name("bob").email("bob@example.com").build())
                .to(ImmutableList.of(DraftEmailer.builder().name("riri").email("riri@example.com").build()))
                .build();

        assertThat(sut.validate()).isEmpty();
    }

    @Test
    public void validateShouldReturnEmptyListWhenSubjectIsNull() {
        testedBuilder = testedBuilder
                .subject(null);

        CreationMessage sut = testedBuilder
                .from(DraftEmailer.builder().name("bob").email("bob@example.com").build())
                .to(ImmutableList.of(DraftEmailer.builder().name("riri").email("riri@example.com").build()))
                .build();

        assertThat(sut.validate()).isEmpty();
    }

    @Test
    public void validateShouldReturnEmptyListWhenSubjectIsEmpty() {
        testedBuilder = testedBuilder
                .subject("");

        CreationMessage sut = testedBuilder
                .from(DraftEmailer.builder().name("bob").email("bob@example.com").build())
                .to(ImmutableList.of(DraftEmailer.builder().name("riri").email("riri@example.com").build()))
                .build();

        assertThat(sut.validate()).isEmpty();
    }

    @Test
    public void mailboxIdShouldSetASingletonList() {
        String mailboxId = "123";
        CreationMessage message = CreationMessage.builder()
            .headers(ImmutableMap.of())
            .mailboxId(mailboxId)
            .build();

        assertThat(message.getMailboxIds()).containsExactly(mailboxId);
    }

    @Test
    public void isDraftShouldBeFalseWhenNoKeywordsSpecified() {
        String mailboxId = "123";
        CreationMessage message = CreationMessage.builder()
            .mailboxId(mailboxId)
            .build();

        assertThat(message.isDraft()).isFalse();
    }

    @Test
    public void isDraftShouldBeTrueWhenOldKeywordDraft() {
        String mailboxId = "123";
        CreationMessage message = CreationMessage.builder()
            .mailboxId(mailboxId)
            .isDraft(Optional.of(true))
            .build();

        assertThat(message.isDraft()).isTrue();
    }

    @Test
    public void isDraftShouldBeFalseWhenOldKeywordNonDraft() {
        String mailboxId = "123";
        CreationMessage message = CreationMessage.builder()
            .mailboxId(mailboxId)
            .isAnswered(Optional.of(true))
            .build();

        assertThat(message.isDraft()).isFalse();
    }

    @Test
    public void isDraftShouldBeFalseWhenEmptyKeywords() {
        String mailboxId = "123";
        CreationMessage message = CreationMessage.builder()
            .keywords(ImmutableMap.of())
            .mailboxId(mailboxId)
            .build();

        assertThat(message.isDraft()).isFalse();
    }

    @Test
    public void isDraftShouldBeFalseWhenKeywordsDoesNotContainsDraft() {
        String mailboxId = "123";
        CreationMessage message = CreationMessage.builder()
            .keywords(ImmutableMap.of(Keyword.ANSWERED.getFlagName(), true))
            .mailboxId(mailboxId)
            .build();

        assertThat(message.isDraft()).isFalse();
    }

    @Test
    public void isDraftShouldBeTrueWhenKeywordsContainsDraft() {
        String mailboxId = "123";
        CreationMessage message = CreationMessage.builder()
            .keywords(ImmutableMap.of(Keyword.DRAFT.getFlagName(), true))
            .mailboxId(mailboxId)
            .build();

        assertThat(message.isDraft()).isTrue();
    }
}