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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class MDNDispositionTest {

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MDNDisposition.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void builderShouldReturnObjectWhenAllFieldsAreValid() {
        assertThat(
            MDNDisposition.builder()
                .actionMode(DispositionActionMode.Automatic)
                .sendingMode(DispositionSendingMode.Automatic)
                .type(DispositionType.Processed)
                .build())
            .isEqualTo(new MDNDisposition(DispositionActionMode.Automatic,
                DispositionSendingMode.Automatic,
                DispositionType.Processed));
    }

    @Test
    public void actionModeIsCompulsory() {
        assertThatThrownBy(() ->
            MDNDisposition.builder()
                .sendingMode(DispositionSendingMode.Automatic)
                .type(DispositionType.Processed)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void sendingModeIsCompulsory() {
        assertThatThrownBy(() ->
            MDNDisposition.builder()
                .actionMode(DispositionActionMode.Automatic)
                .type(DispositionType.Processed)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void typeIsCompulsory() {
        assertThatThrownBy(() ->
            MDNDisposition.builder()
                .actionMode(DispositionActionMode.Automatic)
                .sendingMode(DispositionSendingMode.Automatic)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

}