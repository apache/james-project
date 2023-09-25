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

package org.apache.james.util;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

public class AuditTrail {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditTrail.class);

    public static Entry entry() {
        return new Entry();
    }

    public static class Entry {
        Optional<String> username = Optional.empty();
        Optional<String> remoteIP = Optional.empty();
        Optional<String> userAgent = Optional.empty();
        Optional<String> protocol = Optional.empty();
        Optional<String> action = Optional.empty();
        Map<String, String> parameters = ImmutableMap.of();

        private Entry() {

        }

        public Entry username(String username) {
            this.username = Optional.ofNullable(username);
            return this;
        }

        public Entry remoteIP(String remoteIP) {
            this.remoteIP = Optional.ofNullable(remoteIP);
            return this;
        }

        public Entry userAgent(String userAgent) {
            this.userAgent = Optional.ofNullable(userAgent);
            return this;
        }

        public Entry protocol(String protocol) {
            this.protocol = Optional.ofNullable(protocol);
            return this;
        }

        public Entry action(String action) {
            this.action = Optional.ofNullable(action);
            return this;
        }

        public Entry parameters(Map<String, String> parameters) {
            this.parameters = parameters;
            return this;
        }

        public void log(String message) {
            MDCStructuredLogger.forLogger(LOGGER)
                .field("username", username.orElse(""))
                .field("remoteIP", remoteIP.orElse(""))
                .field("userAgent", userAgent.orElse(""))
                .field("protocol", protocol.orElse(""))
                .field("action", action.orElse(""))
                .field("parameters", StringUtils.join(parameters))
                .log(logger -> logger.info(message));
        }
    }
}
