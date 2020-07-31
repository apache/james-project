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

package org.apache.james.mdn.fields;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.modifier.DispositionModifier;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

class DispositionTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Disposition.class)
            .verify();
    }

    @Test
    void shouldBuildMinimalSubSet() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .build();

        ImmutableList<DispositionModifier> modifiers = ImmutableList.of();
        assertThat(disposition)
            .isEqualTo(new Disposition(
                DispositionActionMode.Automatic,
                DispositionSendingMode.Automatic,
                DispositionType.Processed,
                modifiers));
    }

    @Test
    void buildShouldThrowOnMissingActionMode() {
        assertThatThrownBy(() -> Disposition.builder()
                .sendingMode(DispositionSendingMode.Automatic)
                .type(DispositionType.Processed)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowOnMissingSendingMode() {
        assertThatThrownBy(() -> Disposition.builder()
                .actionMode(DispositionActionMode.Automatic)
                .type(DispositionType.Processed)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowOnMissingType() {
        assertThatThrownBy(() -> Disposition.builder()
                .actionMode(DispositionActionMode.Automatic)
                .sendingMode(DispositionSendingMode.Automatic)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldBuildWithAllOptions() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifiers(DispositionModifier.Expired, DispositionModifier.Warning)
            .build();

        ImmutableList<DispositionModifier> modifiers = ImmutableList.of(DispositionModifier.Expired, DispositionModifier.Warning);
        assertThat(disposition)
            .isEqualTo(new Disposition(
                DispositionActionMode.Automatic,
                DispositionSendingMode.Automatic,
                DispositionType.Processed,
                modifiers));
    }

    @Test
    void formattedValueShouldDisplayAllOptions() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifiers(DispositionModifier.Expired, DispositionModifier.Warning)
            .build();

        assertThat(disposition.formattedValue())
            .isEqualTo("Disposition: automatic-action/MDN-sent-automatically;processed/expired,warning");
    }

    @Test
    void formattedValueShouldDisplaySingleModifier() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifiers(DispositionModifier.Expired)
            .build();

        assertThat(disposition.formattedValue())
            .isEqualTo("Disposition: automatic-action/MDN-sent-automatically;processed/expired");
    }


    @Test
    void formattedValueShouldDisplayNoModifier() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .build();

        assertThat(disposition.formattedValue())
            .isEqualTo("Disposition: automatic-action/MDN-sent-automatically;processed");
    }

    @Test
    void formattedValueShouldDisplayManualActionMode() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifiers(DispositionModifier.Expired)
            .build();

        assertThat(disposition.formattedValue())
            .isEqualTo("Disposition: manual-action/MDN-sent-automatically;processed/expired");
    }

    @Test
    void formattedValueShouldDisplayManualSendingMode() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Processed)
            .addModifiers(DispositionModifier.Expired)
            .build();

        assertThat(disposition.formattedValue())
            .isEqualTo("Disposition: automatic-action/MDN-sent-manually;processed/expired");
    }
}
