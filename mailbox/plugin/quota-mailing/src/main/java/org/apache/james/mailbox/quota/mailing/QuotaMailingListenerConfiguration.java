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

package org.apache.james.mailbox.quota.mailing;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.quota.model.QuotaThreshold;
import org.apache.james.mailbox.quota.model.QuotaThresholds;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class QuotaMailingListenerConfiguration {

    public static class Builder {
        private ImmutableList.Builder<QuotaThreshold> thresholds;
        private Optional<Duration> gradePeriod;
        private Optional<String> bodyTemplate;
        private Optional<String> subjectTemplate;

        private Builder() {
            thresholds = ImmutableList.builder();
            gradePeriod = Optional.empty();
            bodyTemplate = Optional.empty();
            subjectTemplate = Optional.empty();
        }

        public Builder addThreshold(QuotaThreshold quotaThreshold) {
            thresholds.add(quotaThreshold);
            return this;
        }

        public Builder addThresholds(QuotaThreshold... quotaThresholds) {
            thresholds.add(quotaThresholds);
            return this;
        }

        public Builder addThresholds(Collection<QuotaThreshold> quotaThresholds) {
            thresholds.addAll(quotaThresholds);
            return this;
        }

        public Builder withGracePeriod(Duration duration) {
            this.gradePeriod = Optional.of(duration);
            return this;
        }
        
        public Builder withBodyTemplate(String bodyTemplate) {
            this.bodyTemplate = Optional.of(bodyTemplate);
            return this;
        }

        public Builder withSubjectTemplate(String subjectTemplate) {
            this.subjectTemplate = Optional.of(subjectTemplate);
            return this;
        }

        public QuotaMailingListenerConfiguration build() {
            return new QuotaMailingListenerConfiguration(
                new QuotaThresholds(thresholds.build()),
                gradePeriod.orElse(DEFAULT_GRACE_PERIOD),
                bodyTemplate.orElse(DEFAULT_BODY_TEMPLATE),
                subjectTemplate.orElse(DEFAULT_SUBJECT_TEMPLATE));
        }
    }

    public static final String DEFAULT_BODY_TEMPLATE = FileSystem.CLASSPATH_PROTOCOL + "//templates/QuotaThresholdMailBody.mustache";
    public static final String DEFAULT_SUBJECT_TEMPLATE = FileSystem.CLASSPATH_PROTOCOL + "//templates/QuotaThresholdMailSubject.mustache";
    public static final Duration DEFAULT_GRACE_PERIOD = Duration.ofDays(1);

    public static Builder builder() {
        return new Builder();
    }

    private final QuotaThresholds thresholds;
    private final Duration gracePeriod;
    private final String bodyTemplate;
    private final String subjectTemplate;

    private QuotaMailingListenerConfiguration(QuotaThresholds thresholds, Duration gracePeriod, String bodyTemplate, String subjectTemplate) {
        this.thresholds = thresholds;
        this.gracePeriod = gracePeriod;
        this.bodyTemplate = bodyTemplate;
        this.subjectTemplate = subjectTemplate;
    }

    public QuotaThresholds getThresholds() {
        return thresholds;
    }

    public Duration getGracePeriod() {
        return gracePeriod;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    public String getSubjectTemplate() {
        return subjectTemplate;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaMailingListenerConfiguration) {
            QuotaMailingListenerConfiguration that = (QuotaMailingListenerConfiguration) o;

            return Objects.equals(this.thresholds, that.thresholds)
                && Objects.equals(this.gracePeriod, that.gracePeriod)
                && Objects.equals(this.bodyTemplate, that.bodyTemplate)
                && Objects.equals(this.subjectTemplate, that.subjectTemplate);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(thresholds, gracePeriod, bodyTemplate, subjectTemplate);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("thresholds", thresholds)
            .add("gracePeriod", gracePeriod)
            .add("bodyTemplate", bodyTemplate)
            .add("subjectTemplate", subjectTemplate)
            .toString();
    }
}
