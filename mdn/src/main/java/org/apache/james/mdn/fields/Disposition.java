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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.modifier.DispositionModifier;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;

import com.google.common.collect.ImmutableList;

public class Disposition implements Field {
    public static class Builder {
        private Optional<DispositionActionMode> actionMode = Optional.empty();
        private Optional<DispositionSendingMode> sendingMode = Optional.empty();
        private Optional<DispositionType> type = Optional.empty();
        private ImmutableList.Builder<DispositionModifier> modifiers = ImmutableList.builder();

        public Builder actionMode(DispositionActionMode actionMode) {
            this.actionMode = Optional.of(actionMode);
            return this;
        }

        public Builder sendingMode(DispositionSendingMode sendingMode) {
            this.sendingMode = Optional.of(sendingMode);
            return this;
        }

        public Builder type(DispositionType type) {
            this.type = Optional.of(type);
            return this;
        }

        public Builder addModifier(DispositionModifier modifier) {
            this.modifiers.add(modifier);
            return this;
        }

        public Builder addModifiers(DispositionModifier... modifiers) {
            this.modifiers.add(modifiers);
            return this;
        }

        public Disposition build() {
            return new Disposition(actionMode, sendingMode, type, modifiers.build());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final Optional<DispositionActionMode> actionMode;
    private final Optional<DispositionSendingMode> sendingMode;
    private final Optional<DispositionType> type;
    private final List<DispositionModifier> modifiers;

    private Disposition(Optional<DispositionActionMode> actionMode, Optional<DispositionSendingMode> sendingMode, Optional<DispositionType> type, List<DispositionModifier> modifiers) {
        this.actionMode = actionMode;
        this.sendingMode = sendingMode;
        this.type = type;
        this.modifiers = ImmutableList.copyOf(modifiers);
    }

    public Optional<DispositionActionMode> getActionMode() {
        return actionMode;
    }

    public Optional<DispositionSendingMode> getSendingMode() {
        return sendingMode;
    }

    public Optional<DispositionType> getType() {
        return type;
    }

    public List<DispositionModifier> getModifiers() {
        return modifiers;
    }

    @Override
    public String formattedValue() {
        return "Disposition: "
            + actionMode.map(DispositionActionMode::getValue).orElse("") + "/"
            + sendingMode.map(DispositionSendingMode::getValue).orElse("") + ";"
            + type.map(DispositionType::getValue).orElse("")
            + formattedModifiers();
    }

    private CharSequence formattedModifiers() {
        if (modifiers.isEmpty()) {
            return "";
        }
        return "/" + modifiers.stream()
            .map(DispositionModifier::getValue)
            .collect(Collectors.joining(","));
    }
}
