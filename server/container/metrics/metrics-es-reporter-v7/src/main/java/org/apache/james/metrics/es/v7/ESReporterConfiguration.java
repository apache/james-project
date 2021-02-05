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

package org.apache.james.metrics.es.v7;

import java.util.Optional;

import org.apache.james.util.Port;

import com.google.common.base.Preconditions;

public class ESReporterConfiguration {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<String> host = Optional.empty();
        private Optional<Integer> port = Optional.empty();
        private Optional<Boolean> enabled = Optional.empty();
        private Optional<String> index = Optional.empty();
        private Optional<Long> periodInSecond = Optional.empty();

        public Builder enabled() {
            this.enabled = Optional.of(ENABLED);
            return this;
        }

        public Builder disabled() {
            this.enabled = Optional.of(DISABLED);
            return this;
        }

        public Builder onHost(String host, int port) {
            this.host = Optional.of(host);
            this.port = Optional.of(port);
            return this;
        }

        public Builder onIndex(String index) {
            this.index = Optional.ofNullable(index);
            return this;
        }

        public Builder periodInSecond(Long periodInSecond) {
            this.periodInSecond = Optional.ofNullable(periodInSecond);
            return this;
        }

        public ESReporterConfiguration build() {
            Preconditions.checkState(enabled.isPresent(), "You must specify either enabled or disabled");
            Preconditions.checkState(!enabled.get() || host.isPresent(), "You must specify host when enabled");
            Preconditions.checkState(!enabled.get() || port.isPresent(), "You must specify port when enabled");
            if (enabled.get()) {
                Port.assertValid(port.get());
            }
            return new ESReporterConfiguration(host, port, enabled.get(), index, periodInSecond);
        }
    }

    public static final boolean ENABLED = true;
    public static final boolean DISABLED = !ENABLED;
    public static final String DEFAULT_INDEX = "james-metrics";
    public static final long DEFAULT_PERIOD_IN_SECOND = 60L;

    private final Optional<String> host;
    private final Optional<Integer> port;
    private final boolean enabled;
    private final Optional<String> index;
    private final Optional<Long> periodInSecond;

    public ESReporterConfiguration(Optional<String> host, Optional<Integer> port, boolean enabled, Optional<String> index, Optional<Long> periodInSecond) {
        this.host = host;
        this.port = port;
        this.enabled = enabled;
        this.index = index;
        this.periodInSecond = periodInSecond;
    }

    public String getHostWithPort() {
        Preconditions.checkState(host.isPresent());
        Preconditions.checkState(port.isPresent());
        return host.get() + ":" + port.get();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getIndex() {
        return index.orElse(DEFAULT_INDEX);
    }

    public long getPeriodInSecond() {
        return periodInSecond.orElse(DEFAULT_PERIOD_IN_SECOND);
    }
}
