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

package org.apache.james.rspamd.client;

import java.net.URL;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class RspamdClientConfiguration {
    public static final Integer DEFAULT_TIMEOUT_IN_SECONDS = 15;

    public static RspamdClientConfiguration from(Configuration config) {
        URL rspamdUrl = Optional.ofNullable(config.getString("rspamdUrl", null))
            .filter(s -> !s.isEmpty())
            .map(Throwing.function(URL::new))
            .orElseThrow(() -> new IllegalArgumentException("Rspamd's url is invalid."));

        Optional<Integer> rspamdTimeoutConfigure = Optional.ofNullable(config.getInteger("rspamdTimeout", null))
            .map(i -> {
                Preconditions.checkArgument(i > 0, "rspamdTimeout should be positive number");
                return i;
            });

        String rspamdPassword = config.getString("rspamdPassword", "");
        boolean perUserBayes = config.getBoolean("perUserBayes", false);
        return new RspamdClientConfiguration(rspamdUrl, rspamdPassword, rspamdTimeoutConfigure, perUserBayes);
    }

    private final URL url;
    private final String password;
    private final Optional<Integer> timeout;
    private final boolean perUserBayes;

    @VisibleForTesting
    public RspamdClientConfiguration(URL url, String password, Optional<Integer> timeout) {
        this.url = url;
        this.password = password;
        this.timeout = timeout;
        this.perUserBayes = false;
    }

    public RspamdClientConfiguration(URL url, String password, Optional<Integer> timeout, boolean perUserBayes) {
        this.url = url;
        this.password = password;
        this.timeout = timeout;
        this.perUserBayes = perUserBayes;
    }

    public URL getUrl() {
        return url;
    }

    public String getPassword() {
        return password;
    }

    public Optional<Integer> getTimeout() {
        return timeout;
    }

    public boolean usePerUserBayes() {
        return perUserBayes;
    }
}
