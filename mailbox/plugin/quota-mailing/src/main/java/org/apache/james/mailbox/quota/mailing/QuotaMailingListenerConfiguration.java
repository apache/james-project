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
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.quota.model.QuotaThreshold;
import org.apache.james.mailbox.quota.model.QuotaThresholds;
import org.apache.james.util.DurationParser;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class QuotaMailingListenerConfiguration {

    interface XmlKeys {
        String SUBJECT_TEMPLATE = "subjectTemplate";
        String BODY_TEMPLATE = "bodyTemplate";
        String GRACE_PERIOD = "gracePeriod";
        String THRESHOLDS = "thresholds.threshold";
        String THRESHOLD_VALUE = "value";
        String NAME = "name";
    }

    public static QuotaMailingListenerConfiguration from(HierarchicalConfiguration<ImmutableNode> config) {
        return builder()
            .addThresholds(readThresholds(config))
            .subjectTemplate(readSubjectTemplate(config))
            .bodyTemplate(readBodyTemplate(config))
            .gracePeriod(readGracePeriod(config))
            .name(readName(config))
            .build();
    }

    private static Optional<String> readName(HierarchicalConfiguration<ImmutableNode> config) {
        return Optional.ofNullable(config.getString(XmlKeys.NAME, null));
    }

    private static Optional<String> readSubjectTemplate(HierarchicalConfiguration<ImmutableNode> config) {
        return Optional.ofNullable(config.getString(XmlKeys.SUBJECT_TEMPLATE, null));
    }

    private static Optional<String> readBodyTemplate(HierarchicalConfiguration<ImmutableNode> config) {
        return Optional.ofNullable(config.getString(XmlKeys.BODY_TEMPLATE, null));
    }

    private static Optional<Duration> readGracePeriod(HierarchicalConfiguration<ImmutableNode> config) {
        return Optional.ofNullable(config.getString(XmlKeys.GRACE_PERIOD, null))
            .map(string -> DurationParser.parse(string, ChronoUnit.DAYS));
    }

    private static ImmutableMap<QuotaThreshold, RenderingInformation> readThresholds(HierarchicalConfiguration<ImmutableNode> config) {
        return config.configurationsAt(XmlKeys.THRESHOLDS)
            .stream()
            .map(node -> Pair.of(
                node.getDouble(XmlKeys.THRESHOLD_VALUE),
                RenderingInformation.from(
                    Optional.ofNullable(node.getString(XmlKeys.BODY_TEMPLATE)),
                    Optional.ofNullable(node.getString(XmlKeys.SUBJECT_TEMPLATE)))))
            .collect(Guavate.toImmutableMap(
                pair -> new QuotaThreshold(pair.getLeft()),
                Pair::getRight));
    }

    public static class RenderingInformation {
        private final Optional<String> bodyTemplate;
        private final Optional<String> subjectTemplate;

        public static RenderingInformation from(Optional<String> bodyTemplate, Optional<String> subjectTemplate) {
            return new RenderingInformation(
                bodyTemplate,
                subjectTemplate);
        }

        public static RenderingInformation from(String bodyTemplate, String subjectTemplate) {
            return from(Optional.of(bodyTemplate), Optional.of(subjectTemplate));
        }

        private RenderingInformation(Optional<String> bodyTemplate, Optional<String> subjectTemplate) {
            Preconditions.checkArgument(!bodyTemplate.equals(Optional.of("")), "Pass a non empty bodyTemplate");
            Preconditions.checkArgument(!subjectTemplate.equals(Optional.of("")), "Pass a non empty subjectTemplate");
            this.bodyTemplate = bodyTemplate;
            this.subjectTemplate = subjectTemplate;
        }

        public Optional<String> getBodyTemplate() {
            return bodyTemplate;
        }

        public Optional<String> getSubjectTemplate() {
            return subjectTemplate;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof RenderingInformation) {
                RenderingInformation that = (RenderingInformation) o;

                return Objects.equals(this.bodyTemplate, that.bodyTemplate)
                    && Objects.equals(this.subjectTemplate, that.subjectTemplate);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(bodyTemplate, subjectTemplate);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("bodyTemplate", bodyTemplate)
                .add("subjectTemplate", subjectTemplate)
                .toString();
        }
    }

    public static class Builder {
        private ImmutableList.Builder<QuotaThreshold> thresholds;
        private ImmutableMap.Builder<QuotaThreshold, RenderingInformation> toRenderingInformation;
        private Optional<Duration> gradePeriod;
        private Optional<String> bodyTemplate;
        private Optional<String> subjectTemplate;
        private Optional<String> name;

        private Builder() {
            thresholds = ImmutableList.builder();
            toRenderingInformation = ImmutableMap.builder();
            gradePeriod = Optional.empty();
            bodyTemplate = Optional.empty();
            subjectTemplate = Optional.empty();
            name = Optional.empty();
        }

        public Builder addThreshold(QuotaThreshold quotaThreshold, RenderingInformation renderingInformation) {
            thresholds.add(quotaThreshold);
            toRenderingInformation.put(quotaThreshold, renderingInformation);
            return this;
        }

        public Builder addThreshold(QuotaThreshold quotaThreshold) {
            thresholds.add(quotaThreshold);
            return this;
        }

        public Builder addThresholds(QuotaThreshold... quotaThresholds) {
            Arrays.stream(quotaThresholds)
                .forEach(this::addThreshold);
            return this;
        }

        public Builder addThresholds(Collection<QuotaThreshold> quotaThresholds) {
            quotaThresholds.forEach(this::addThreshold);
            return this;
        }

        public Builder addThresholds(ImmutableMap<QuotaThreshold, RenderingInformation> quotaThresholds) {
            quotaThresholds.forEach(this::addThreshold);
            return this;
        }

        public Builder gracePeriod(Duration duration) {
            this.gradePeriod = Optional.of(duration);
            return this;
        }
        
        public Builder bodyTemplate(String bodyTemplate) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(bodyTemplate), "Pass a non null/empty bodyTemplate");
            this.bodyTemplate = Optional.of(bodyTemplate);
            return this;
        }

        public Builder subjectTemplate(String subjectTemplate) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(subjectTemplate), "Pass a non null/empty subjectTemplate");
            this.subjectTemplate = Optional.of(subjectTemplate);
            return this;
        }

        public Builder bodyTemplate(Optional<String> bodyTemplate) {
            bodyTemplate.ifPresent(this::bodyTemplate);
            return this;
        }

        public Builder subjectTemplate(Optional<String> subjectTemplate) {
            subjectTemplate.ifPresent(this::subjectTemplate);
            return this;
        }

        public Builder gracePeriod(Optional<Duration> duration) {
            duration.ifPresent(this::gracePeriod);
            return this;
        }

        public Builder name(String name) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "Pass a non null/empty name");
            this.name = Optional.of(name);
            return this;
        }

        public Builder name(Optional<String> name) {
            name.ifPresent(this::name);
            return this;
        }

        public QuotaMailingListenerConfiguration build() {
            return new QuotaMailingListenerConfiguration(
                toRenderingInformation.build(),
                new QuotaThresholds(thresholds.build()),
                gradePeriod.orElse(DEFAULT_GRACE_PERIOD),
                bodyTemplate,
                subjectTemplate,
                name.orElse(DEFAULT_NAME));
        }
    }

    public static final String DEFAULT_BODY_TEMPLATE = FileSystem.CLASSPATH_PROTOCOL + "//templates/QuotaThresholdMailBody.mustache";
    public static final String DEFAULT_SUBJECT_TEMPLATE = FileSystem.CLASSPATH_PROTOCOL + "//templates/QuotaThresholdMailSubject.mustache";
    public static final RenderingInformation DEFAULT_RENDERING_INFORMATION = RenderingInformation.from(Optional.empty(), Optional.empty());
    public static final Duration DEFAULT_GRACE_PERIOD = Duration.ofDays(1);
    private static final String DEFAULT_NAME = "default";

    public static Builder builder() {
        return new Builder();
    }

    public static QuotaMailingListenerConfiguration defaultConfiguration() {
        return builder().build();
    }


    private final ImmutableMap<QuotaThreshold, RenderingInformation> toRenderingInformation;
    private final QuotaThresholds thresholds;
    private final Duration gracePeriod;
    private final Optional<String> bodyTemplate;
    private final Optional<String> subjectTemplate;
    private final String name;

    private QuotaMailingListenerConfiguration(ImmutableMap<QuotaThreshold, RenderingInformation> toRenderingInformation,
                                              QuotaThresholds thresholds, Duration gracePeriod, Optional<String> bodyTemplate, Optional<String> subjectTemplate, String name) {
        this.toRenderingInformation = toRenderingInformation;
        this.thresholds = thresholds;
        this.gracePeriod = gracePeriod;
        this.bodyTemplate = bodyTemplate;
        this.subjectTemplate = subjectTemplate;
        this.name = name;
    }

    public QuotaThresholds getThresholds() {
        return thresholds;
    }

    public Duration getGracePeriod() {
        return gracePeriod;
    }

    public String getBodyTemplate(QuotaThreshold quotaThreshold) {
        return Optional
            .ofNullable(
                toRenderingInformation.get(quotaThreshold))
                    .flatMap(RenderingInformation::getBodyTemplate)
            .or(() -> bodyTemplate)
            .orElse(DEFAULT_BODY_TEMPLATE);
    }

    public String getSubjectTemplate(QuotaThreshold quotaThreshold) {
        return Optional
            .ofNullable(
                toRenderingInformation.get(quotaThreshold))
                    .flatMap(RenderingInformation::getSubjectTemplate)
            .or(() -> subjectTemplate)
            .orElse(DEFAULT_SUBJECT_TEMPLATE);
    }

    public String getName() {
        return name;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaMailingListenerConfiguration) {
            QuotaMailingListenerConfiguration that = (QuotaMailingListenerConfiguration) o;

            return Objects.equals(this.toRenderingInformation, that.toRenderingInformation)
                && Objects.equals(this.thresholds, that.thresholds)
                && Objects.equals(this.gracePeriod, that.gracePeriod)
                && Objects.equals(this.subjectTemplate, that.subjectTemplate)
                && Objects.equals(this.bodyTemplate, that.bodyTemplate)
                && Objects.equals(this.name, that.name);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(toRenderingInformation, thresholds, subjectTemplate, bodyTemplate, gracePeriod, name);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("toRenderingInformation", toRenderingInformation)
            .add("thresholds", thresholds)
            .add("bodyTemplate", bodyTemplate)
            .add("subjectTemplate", subjectTemplate)
            .add("gracePeriod", gracePeriod)
            .add("name", name)
            .toString();
    }
}
