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

package org.apache.james.domainlist.lib;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.util.StreamUtils;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class DomainListConfiguration {
    public static class Builder {
        private Optional<Boolean> autoDetectIp;
        private Optional<Boolean> autoDetect;
        private Optional<Domain> defaultDomain;
        private ImmutableList.Builder<Domain> configuredDomains;

        public Builder() {
            autoDetectIp = Optional.empty();
            autoDetect = Optional.empty();
            defaultDomain = Optional.empty();
            configuredDomains = ImmutableList.builder();
        }

        public Builder defaultDomain(Domain defaultDomain) {
            this.defaultDomain = Optional.of(defaultDomain);
            return this;
        }

        public Builder autoDetect(boolean autoDetect) {
            this.autoDetect = Optional.of(autoDetect);
            return this;
        }

        public Builder autoDetectIp(boolean autoDetectIp) {
            this.autoDetectIp = Optional.of(autoDetectIp);
            return this;
        }

        public Builder defaultDomain(Optional<Domain> defaultDomain) {
            this.defaultDomain = defaultDomain;
            return this;
        }

        public Builder autoDetect(Optional<Boolean> autoDetect) {
            this.autoDetect = autoDetect;
            return this;
        }

        public Builder autoDetectIp(Optional<Boolean> autoDetectIp) {
            this.autoDetectIp = autoDetectIp;
            return this;
        }

        public Builder addConfiguredDomain(Domain domain) {
            this.configuredDomains.add(domain);
            return this;
        }

        public Builder addConfiguredDomains(Collection<Domain> domains) {
            this.configuredDomains.addAll(domains);
            return this;
        }

        public Builder addConfiguredDomains(Domain... domains) {
            return this.addConfiguredDomains(Arrays.asList(domains));
        }

        public DomainListConfiguration build() {
            return new DomainListConfiguration(
                autoDetectIp.orElse(false),
                autoDetect.orElse(false),
                defaultDomain.orElse(Domain.LOCALHOST),
                configuredDomains.build());
        }
    }

    public static DomainListConfiguration DEFAULT = builder().build();

    public static final String CONFIGURE_AUTODETECT = "autodetect";
    public static final String CONFIGURE_AUTODETECT_IP = "autodetectIP";
    public static final String CONFIGURE_DEFAULT_DOMAIN = "defaultDomain";
    public static final String CONFIGURE_DOMAIN_NAMES = "domainnames.domainname";

    public static Builder builder() {
        return new Builder();
    }

    public static DomainListConfiguration from(HierarchicalConfiguration<ImmutableNode> config) {
        ImmutableList<Domain> configuredDomains = StreamUtils.ofNullable(config.getStringArray(CONFIGURE_DOMAIN_NAMES))
            .filter(Predicate.not(String::isEmpty))
            .map(Domain::of)
            .collect(Guavate.toImmutableList());

        return builder()
            .autoDetect(Optional.ofNullable(config.getBoolean(CONFIGURE_AUTODETECT, null)))
            .autoDetectIp(Optional.ofNullable(config.getBoolean(CONFIGURE_AUTODETECT_IP, null)))
            .defaultDomain(Optional.ofNullable(config.getString(CONFIGURE_DEFAULT_DOMAIN, null))
                .map(Domain::of))
            .addConfiguredDomains(configuredDomains)
            .build();
    }

    private final boolean autoDetectIp;
    private final boolean autoDetect;
    private final Domain defaultDomain;
    private final List<Domain> configuredDomains;

    public DomainListConfiguration(boolean autoDetectIp, boolean autoDetect, Domain defaultDomain, List<Domain> configuredDomains) {
        this.autoDetectIp = autoDetectIp;
        this.autoDetect = autoDetect;
        this.defaultDomain = defaultDomain;
        this.configuredDomains = configuredDomains;
    }

    public boolean isAutoDetectIp() {
        return autoDetectIp;
    }

    public boolean isAutoDetect() {
        return autoDetect;
    }

    public Domain getDefaultDomain() {
        return defaultDomain;
    }

    public List<Domain> getConfiguredDomains() {
        return configuredDomains;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof DomainListConfiguration) {
            DomainListConfiguration that = (DomainListConfiguration) o;

            return Objects.equals(this.autoDetectIp, that.autoDetectIp)
                && Objects.equals(this.autoDetect, that.autoDetect)
                && Objects.equals(this.defaultDomain, that.defaultDomain);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(autoDetectIp, autoDetect, defaultDomain);
    }
}
