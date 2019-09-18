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

import java.util.Objects;

import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

@JsonDeserialize(builder = MDNDisposition.Builder.class)
public class MDNDisposition {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private DispositionActionMode actionMode;
        private DispositionSendingMode sendingMode;
        private DispositionType type;

        public Builder actionMode(DispositionActionMode actionMode) {
            this.actionMode = actionMode;
            return this;
        }

        public Builder sendingMode(DispositionSendingMode sendingMode) {
            this.sendingMode = sendingMode;
            return this;
        }

        public Builder type(DispositionType type) {
            this.type = type;
            return this;
        }

        public MDNDisposition build() {
            Preconditions.checkState(actionMode != null, "'actionMode' is mandatory");
            Preconditions.checkState(sendingMode != null, "'sendingMode' is mandatory");
            Preconditions.checkState(type != null, "'type' is mandatory");

            return new MDNDisposition(actionMode, sendingMode, type);
        }
    }

    private final DispositionActionMode actionMode;
    private final DispositionSendingMode sendingMode;
    private final DispositionType type;

    @VisibleForTesting
    MDNDisposition(DispositionActionMode actionMode, DispositionSendingMode sendingMode, DispositionType type) {
        this.actionMode = actionMode;
        this.sendingMode = sendingMode;
        this.type = type;
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

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MDNDisposition) {
            MDNDisposition that = (MDNDisposition) o;

            return Objects.equals(this.actionMode, that.actionMode)
                && Objects.equals(this.sendingMode, that.sendingMode)
                && Objects.equals(this.type, that.type);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(actionMode, sendingMode, type);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("actionMode", actionMode)
            .add("sendingMode", sendingMode)
            .add("type", type)
            .toString();
    }
}
