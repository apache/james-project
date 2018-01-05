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

package org.apache.james.mailbox.tika;

import java.util.Optional;

import org.apache.james.util.Port;

import com.google.common.base.Preconditions;

public class TikaConfiguration {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Optional<String> host;
        private Optional<Integer> port;
        private Optional<Integer> timeoutInMillis;

        private Builder() {
            host = Optional.empty();
            port = Optional.empty();
            timeoutInMillis = Optional.empty();
        }

        public Builder host(String host) {
            this.host = Optional.ofNullable(host);
            return this;
        }

        public Builder port(int port) {
            this.port = Optional.of(port);
            return this;
        }

        public Builder timeoutInMillis(int timeoutInMillis) {
            this.timeoutInMillis = Optional.of(timeoutInMillis);
            return this;
        }

        public TikaConfiguration build() {
            Preconditions.checkState(host.isPresent(), "'host' is mandatory");
            Preconditions.checkState(port.isPresent(), "'port' is mandatory");
            Preconditions.checkState(timeoutInMillis.isPresent(), "'timeoutInMillis' is mandatory");
            Port.assertValid(port.get());

            return new TikaConfiguration(host.get(), port.get(), timeoutInMillis.get());
        }
    }

    private final String host;
    private final int port;
    private final int timeoutInMillis;

    private TikaConfiguration(String host, int port, int timeoutInMillis) {
        this.host = host;
        this.port = port;
        this.timeoutInMillis = timeoutInMillis;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getTimeoutInMillis() {
        return timeoutInMillis;
    }
}
