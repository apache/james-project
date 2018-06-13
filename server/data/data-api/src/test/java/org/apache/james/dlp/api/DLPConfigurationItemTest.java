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

package org.apache.james.dlp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class DLPConfigurationItemTest {

    private static final String EXPLANATION = "explanation";
    private static final String REGEX = "regex";
    public static final DLPConfigurationItem.Id UNIQUE_ID = DLPConfigurationItem.Id.of("uniqueId");

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(DLPConfigurationItem.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    void innerClassTargetsShouldMatchBeanContract() {
        EqualsVerifier.forClass(DLPConfigurationItem.Targets.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    void innerClassIdShouldMatchBeanContract() {
        EqualsVerifier.forClass(DLPConfigurationItem.Targets.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    void idShouldThrowOnNull() {
        assertThatThrownBy(() -> DLPConfigurationItem.Id.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void idShouldThrowOnEmpty() {
        assertThatThrownBy(() -> DLPConfigurationItem.Id.of("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idShouldThrowOnBlank() {
        assertThatThrownBy(() -> DLPConfigurationItem.Id.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idShouldBeMandatory() {
        assertThatThrownBy(() ->
            DLPConfigurationItem.builder()
                .expression("my expression")
                .targetsRecipients()
                .targetsSender()
                .targetsContent()
                .explanation(EXPLANATION)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expressionShouldBeMandatory() {
        assertThatThrownBy(() ->
            DLPConfigurationItem.builder()
                .id(UNIQUE_ID)
                .targetsRecipients()
                .targetsSender()
                .targetsContent()
                .explanation(EXPLANATION)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expressionAndIdShouldBeTheOnlyMandatoryFields() {
        assertThatCode(() ->
            DLPConfigurationItem.builder()
                .id(UNIQUE_ID)
                .expression(REGEX)
                .build())
            .doesNotThrowAnyException();
    }

    @Test
    void builderShouldPreserveExpression() {
        DLPConfigurationItem dlpConfigurationItem = DLPConfigurationItem.builder()
            .id(UNIQUE_ID)
            .expression(REGEX)
            .build();

        assertThat(dlpConfigurationItem.getRegexp()).isEqualTo(REGEX);
    }

    @Test
    void builderShouldPreserveExplanation() {
        DLPConfigurationItem dlpConfigurationItem = DLPConfigurationItem.builder()
            .id(UNIQUE_ID)
            .explanation(EXPLANATION)
            .expression(REGEX)
            .build();

        assertThat(dlpConfigurationItem.getExplanation()).contains(EXPLANATION);
    }

    @Test
    void dlpRuleShouldHaveNoTargetsWhenNoneSpecified() {
        DLPConfigurationItem dlpConfigurationItem = DLPConfigurationItem.builder()
            .id(UNIQUE_ID)
            .expression(REGEX)
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(dlpConfigurationItem.getTargets().isContentTargeted()).isFalse();
            softly.assertThat(dlpConfigurationItem.getTargets().isRecipientTargeted()).isFalse();
            softly.assertThat(dlpConfigurationItem.getTargets().isSenderTargeted()).isFalse();
        });
    }

    @Test
    void targetsRecipientsShouldBeReportedInTargets() {
        DLPConfigurationItem dlpConfigurationItem = DLPConfigurationItem.builder()
            .id(UNIQUE_ID)
            .targetsRecipients()
            .expression(REGEX)
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(dlpConfigurationItem.getTargets().isContentTargeted()).isFalse();
            softly.assertThat(dlpConfigurationItem.getTargets().isRecipientTargeted()).isTrue();
            softly.assertThat(dlpConfigurationItem.getTargets().isSenderTargeted()).isFalse();
        });
    }

    @Test
    void targetsSenderShouldBeReportedInTargets() {
        DLPConfigurationItem dlpConfigurationItem = DLPConfigurationItem.builder()
            .id(UNIQUE_ID)
            .targetsSender()
            .expression(REGEX)
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(dlpConfigurationItem.getTargets().isContentTargeted()).isFalse();
            softly.assertThat(dlpConfigurationItem.getTargets().isRecipientTargeted()).isFalse();
            softly.assertThat(dlpConfigurationItem.getTargets().isSenderTargeted()).isTrue();
        });
    }

    @Test
    void targetsContentShouldBeReportedInTargets() {
        DLPConfigurationItem dlpConfigurationItem = DLPConfigurationItem.builder()
            .id(UNIQUE_ID)
            .targetsContent()
            .expression(REGEX)
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(dlpConfigurationItem.getTargets().isContentTargeted()).isTrue();
            softly.assertThat(dlpConfigurationItem.getTargets().isRecipientTargeted()).isFalse();
            softly.assertThat(dlpConfigurationItem.getTargets().isSenderTargeted()).isFalse();
        });
    }

    @Test
    void allTargetsShouldBeReportedInTargets() {
        DLPConfigurationItem dlpConfigurationItem = DLPConfigurationItem.builder()
            .id(UNIQUE_ID)
            .targetsContent()
            .targetsSender()
            .targetsRecipients()
            .expression(REGEX)
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(dlpConfigurationItem.getTargets().isContentTargeted()).isTrue();
            softly.assertThat(dlpConfigurationItem.getTargets().isRecipientTargeted()).isTrue();
            softly.assertThat(dlpConfigurationItem.getTargets().isSenderTargeted()).isTrue();
        });
    }


}