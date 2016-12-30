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

package org.apache.james.metrics.dropwizard;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

public class ESReporterConfiguration {

    public static final boolean ENABLED = true;
    public static final boolean DISABLED = !ENABLED;
    public static final String DEFAULT_INDEX = "james-metrics";
    public static final long DEFAULT_PERIOD_IN_SECOND = 60L;

    public static ESReporterConfiguration disabled() {
        return new ESReporterConfiguration(
            Optional.<String>absent(),
            Optional.<Integer>absent(),
            DISABLED,
            Optional.<String>absent(),
            Optional.<Long>absent());
    }

    public static ESReporterConfiguration enabled(String host, int port, Optional<String> index, Optional<Long> periodInSecond) {
        return new ESReporterConfiguration(
            Optional.of(host),
            Optional.of(port),
            ENABLED,
            index,
            periodInSecond);
    }

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
        return index.or(DEFAULT_INDEX);
    }

    public long getPeriodInSecond() {
        return periodInSecond.or(DEFAULT_PERIOD_IN_SECOND);
    }
}
