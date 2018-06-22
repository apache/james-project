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

package org.apache.james.webadmin.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.james.dlp.api.DLPConfigurationItem;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class DLPConfigurationItemDTOTest {

    private static final String ID = "id";
    private static final String EXPRESSION = "expression";
    private static final String EXPLANATION = "explanation";
    private static final String NULL_ID = null;
    private static final String NULL_EXPRESSION = null;

    @Test
    void toDTOsShouldSetAllFields() {
        DLPConfigurationItemDTO dto = DLPConfigurationItemDTO.toDTO(
            DLPConfigurationItem.builder()
                .id(DLPConfigurationItem.Id.of(ID))
                .expression(EXPRESSION)
                .explanation(EXPLANATION)
                .targetsSender(true)
                .targetsRecipients(true)
                .targetsContent(false)
                .build());

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(dto.getId()).isEqualTo(ID);
            softly.assertThat(dto.getExpression()).isEqualTo(EXPRESSION);
            softly.assertThat(dto.getExplanation().get()).isEqualTo(EXPLANATION);
            softly.assertThat(dto.getTargetsSender()).isTrue();
            softly.assertThat(dto.getTargetsRecipients()).isTrue();
            softly.assertThat(dto.getTargetsContent()).isFalse();
        });
    }

    @Test
    void toDLPConfigurationsShouldSetAllFields() {
        DLPConfigurationItemDTO itemDTO = new DLPConfigurationItemDTO(
            ID,
            EXPRESSION,
            Optional.of(EXPLANATION),
            true,
            true,
            true);
        DLPConfigurationItem item = itemDTO.toDLPConfiguration();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(item.getId().asString()).isEqualTo(ID);
            softly.assertThat(item.getRegexp().pattern()).isEqualTo(EXPRESSION);
            softly.assertThat(item.getExplanation().get()).isEqualTo(EXPLANATION);
            softly.assertThat(item.getTargets().isSenderTargeted()).isTrue();
            softly.assertThat(item.getTargets().isRecipientTargeted()).isTrue();
            softly.assertThat(item.getTargets().isContentTargeted()).isTrue();
        });
    }

    @Test
    void constructorShouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> new DLPConfigurationItemDTO(NULL_ID,
                EXPRESSION,
                Optional.of(EXPLANATION),
                true,
                true,
                true))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenExpressionIsNull() {
        assertThatThrownBy(() -> new DLPConfigurationItemDTO(ID,
                NULL_EXPRESSION,
                Optional.of(EXPLANATION),
                true,
                true,
                true))
            .isInstanceOf(NullPointerException.class);
    }
}
