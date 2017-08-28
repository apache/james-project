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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Implements disposition as stated in https://tools.ietf.org/html/rfc8098#section-3.2.6
 */
public class Disposition implements Field {
    public static final String FIELD_NAME = "Disposition";

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
            Preconditions.checkState(actionMode.isPresent());
            Preconditions.checkState(sendingMode.isPresent());
            Preconditions.checkState(type.isPresent());

            return new Disposition(actionMode.get(), sendingMode.get(), type.get(), modifiers.build());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final DispositionActionMode actionMode;
    private final DispositionSendingMode sendingMode;
    private final DispositionType type;
    private final List<DispositionModifier> modifiers;

    private Disposition(DispositionActionMode actionMode, DispositionSendingMode sendingMode, DispositionType type, List<DispositionModifier> modifiers) {
        this.actionMode = actionMode;
        this.sendingMode = sendingMode;
        this.type = type;
        this.modifiers = ImmutableList.copyOf(modifiers);
    }

    public DispositionActionMode getActionMode() {
        return actionMode;
    }

    public DispositionSendingMode getSendingMode() {
        return sendingMode;
    }

    public DispositionType getType() {
        return type;
    }

    public List<DispositionModifier> getModifiers() {
        return modifiers;
    }

    @Override
    public String formattedValue() {
        return FIELD_NAME + ": "
            + actionMode.getValue() + "/" + sendingMode.getValue() + ";" + type.getValue()
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
