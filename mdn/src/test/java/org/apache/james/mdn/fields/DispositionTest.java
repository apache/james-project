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

import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.modifier.DispositionModifier;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

public class DispositionTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldMatchBeanContract() throws Exception {
        EqualsVerifier.forClass(Disposition.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void shouldBuildMinimalSubSet() {
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
    public void buildShouldThrowOnMissingActionMode() {
        expectedException.expect(IllegalStateException.class);

        Disposition.builder()
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .build();
    }

    @Test
    public void buildShouldThrowOnMissingSendingMode() {
        expectedException.expect(IllegalStateException.class);

        Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .type(DispositionType.Processed)
            .build();
    }

    @Test
    public void buildShouldThrowOnMissingType() {
        expectedException.expect(IllegalStateException.class);

        Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .build();
    }

    @Test
    public void shouldBuildWithAllOptions() {
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
    public void formattedValueShouldDisplayAllOptions() {
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
    public void formattedValueShouldDisplaySingleModifier() {
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
    public void formattedValueShouldDisplayNoModifier() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .build();

        assertThat(disposition.formattedValue())
            .isEqualTo("Disposition: automatic-action/MDN-sent-automatically;processed");
    }

    @Test
    public void formattedValueShouldDisplayManualActionMode() {
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
    public void formattedValueShouldDisplayManualSendingMode() {
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
