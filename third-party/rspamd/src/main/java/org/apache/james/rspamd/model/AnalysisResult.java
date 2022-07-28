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

package org.apache.james.rspamd.model;

import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisResult {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Action action;
        private float score;
        private float requiredScore;
        private Optional<String> desiredRewriteSubject;

        public Builder() {
            desiredRewriteSubject = Optional.empty();
        }

        public Builder action(Action action) {
            this.action = action;
            return this;
        }

        public Builder score(float score) {
            this.score = score;
            return this;
        }

        public Builder requiredScore(float requiredScore) {
            this.requiredScore = requiredScore;
            return this;
        }

        public Builder desiredRewriteSubject(String desiredRewriteSubject) {
            this.desiredRewriteSubject = Optional.of(desiredRewriteSubject);
            return this;
        }

        public AnalysisResult build() {
            Preconditions.checkNotNull(action);

            return new AnalysisResult(action, score, requiredScore, desiredRewriteSubject);
        }
    }

    public enum Action {
        @JsonProperty("no action") NO_ACTION("no action"), // message is likely ham
        @JsonProperty("greylist") GREY_LIST("greylist"), // message should be grey listed
        @JsonProperty("add header") ADD_HEADER("add header"), // message is suspicious and should be marked as spam
        @JsonProperty("rewrite subject") REWRITE_SUBJECT("rewrite subject"), // message is suspicious and should have subject rewritten
        @JsonProperty("soft reject") SOFT_REJECT("soft reject"), // message should be temporary rejected (for example, due to rate limit exhausting)
        @JsonProperty("reject") REJECT("reject"); // message should be rejected as spam

        private final String description;

        Action(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private final Action action;
    private final float score;
    private final float requiredScore;
    private final Optional<String> desiredRewriteSubject;

    public AnalysisResult(@JsonProperty("action") Action action,
                          @JsonProperty("score") float score,
                          @JsonProperty("required_score") float requiredScore,
                          @JsonProperty("subject") Optional<String> desiredRewriteSubject) {
        this.action = action;
        this.score = score;
        this.requiredScore = requiredScore;
        this.desiredRewriteSubject = desiredRewriteSubject;
    }

    public Action getAction() {
        return action;
    }

    public float getScore() {
        return score;
    }

    public float getRequiredScore() {
        return requiredScore;
    }

    public Optional<String> getDesiredRewriteSubject() {
        return desiredRewriteSubject;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof AnalysisResult) {
            AnalysisResult that = (AnalysisResult) o;

            return Objects.equals(this.score, that.score)
                && Objects.equals(this.requiredScore, that.requiredScore)
                && Objects.equals(this.action, that.action)
                && Objects.equals(this.desiredRewriteSubject, that.desiredRewriteSubject);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(action, score, requiredScore, desiredRewriteSubject);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("action", action)
            .add("score", score)
            .add("requiredScore", requiredScore)
            .add("desiredRewriteSubject", desiredRewriteSubject)
            .toString();
    }
}
