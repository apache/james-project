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

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class DLPConfigurationItem {

    public static class Id {

        public static Id of(String id) {
            Preconditions.checkNotNull(id, "id should no be null");
            Preconditions.checkArgument(StringUtils.isNotBlank(id), "id should no be empty");
            return new Id(id);
        }

        private final String value;

        private Id(String value) {
            this.value = value;
        }

        public String asString() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Id) {
                Id id = (Id) o;
                return Objects.equals(value, id.value);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("value", value)
                .toString();
        }
    }

    public static class Builder {
        private static final boolean NOT_TARGETED = false;

        private Optional<Boolean> targetsSender;
        private Optional<Boolean> targetsRecipients;
        private Optional<Boolean> targetsContent;
        private Optional<String> explanation;
        private Optional<String> expression;
        private Optional<Id> id;

        public Builder() {
            targetsSender = Optional.empty();
            targetsRecipients = Optional.empty();
            targetsContent = Optional.empty();
            explanation = Optional.empty();
            expression = Optional.empty();
            id = Optional.empty();
        }

        public Builder targetsSender() {
            this.targetsSender = Optional.of(true);
            return this;
        }

        public Builder targetsSender(boolean targetsSender) {
            this.targetsSender = Optional.of(targetsSender);
            return this;
        }

        public Builder targetsRecipients() {
            this.targetsRecipients = Optional.of(true);
            return this;
        }

        public Builder targetsRecipients(boolean targetsRecipients) {
            this.targetsRecipients = Optional.of(targetsRecipients);
            return this;
        }

        public Builder targetsContent() {
            this.targetsContent = Optional.of(true);
            return this;
        }

        public Builder targetsContent(boolean targetsContent) {
            this.targetsContent = Optional.of(targetsContent);
            return this;
        }

        public Builder expression(String expression) {
            this.expression = Optional.of(expression);
            return this;
        }

        public Builder expression(Optional<String> expression) {
            expression.ifPresent(this::expression);
            return this;
        }

        public Builder explanation(String explanation) {
            this.explanation = Optional.of(explanation);
            return this;
        }

        public Builder explanation(Optional<String> explanation) {
            explanation.ifPresent(this::explanation);
            return this;
        }

        public Builder id(Id id) {
            this.id = Optional.of(id);
            return this;
        }

        public DLPConfigurationItem build() {
            Preconditions.checkState(id.isPresent(), "`id` is mandatory");
            Preconditions.checkState(expression.isPresent(), "`expression` is mandatory");

            return new DLPConfigurationItem(
                id.get(),
                explanation,
                ensureValidPattern(expression.get()),
                new Targets(
                    targetsSender.orElse(NOT_TARGETED),
                    targetsRecipients.orElse(NOT_TARGETED),
                    targetsContent.orElse(NOT_TARGETED)));
        }

        private Pattern ensureValidPattern(String input) {
            return isValidPattern(input)
                .orElseThrow(() -> new IllegalStateException("`expression` must be a valid regex"));
        }

        private static Optional<Pattern> isValidPattern(String regex) {
            try {
                return Optional.of(Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                return Optional.empty();
            }
        }
    }

    public static class Targets {
        private final boolean senderTargeted;
        private final boolean recipientTargeted;
        private final boolean contentTargeted;

        public Targets(boolean senderTargeted, boolean recipientTargeted, boolean contentTargeted) {
            this.senderTargeted = senderTargeted;
            this.recipientTargeted = recipientTargeted;
            this.contentTargeted = contentTargeted;
        }

        public boolean isSenderTargeted() {
            return senderTargeted;
        }

        public boolean isRecipientTargeted() {
            return recipientTargeted;
        }

        public boolean isContentTargeted() {
            return contentTargeted;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Targets) {
                Targets targets = (Targets) o;

                return Objects.equals(this.senderTargeted, targets.senderTargeted)
                    && Objects.equals(this.recipientTargeted, targets.recipientTargeted)
                    && Objects.equals(this.contentTargeted, targets.contentTargeted);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(senderTargeted, recipientTargeted, contentTargeted);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("senderTargeted", senderTargeted)
                .add("recipientTargeted", recipientTargeted)
                .add("contentTargeted", contentTargeted)
                .toString();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final Id id;
    private final Optional<String> explanation;
    private final Pattern regexp;
    private final Targets targets;

    private DLPConfigurationItem(Id id, Optional<String> explanation, Pattern regexp, Targets targets) {
        this.id = id;
        this.explanation = explanation;
        this.regexp = regexp;
        this.targets = targets;
    }

    public Optional<String> getExplanation() {
        return explanation;
    }

    public Pattern getRegexp() {
        return regexp;
    }

    public Targets getTargets() {
        return targets;
    }

    public Id getId() {
        return id;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof DLPConfigurationItem) {
            DLPConfigurationItem dlpConfigurationItem = (DLPConfigurationItem) o;

            Optional<String> regexp = Optional.ofNullable(this.regexp).map(Pattern::pattern);
            Optional<String> otherRegexp = Optional.ofNullable(dlpConfigurationItem.regexp).map(Pattern::pattern);
            return Objects.equals(this.id, dlpConfigurationItem.id)
                && Objects.equals(this.explanation, dlpConfigurationItem.explanation)
                && Objects.equals(regexp, otherRegexp)
                && Objects.equals(this.targets, dlpConfigurationItem.targets);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id, explanation, regexp, targets);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("explanation", explanation)
            .add("regexp", regexp)
            .add("targets", targets)
            .toString();
    }
}
