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

package org.apache.james.crowdsec.client;

import java.net.URL;
import java.time.Duration;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

import com.github.fge.lambdas.Throwing;


public class CrowdsecClientConfiguration {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5L);
    public static final String DEFAULT_API_KEY = "default_api_key";

    private final URL url;
    private final String apiKey;

    public static CrowdsecClientConfiguration from(Configuration config) {
        URL crowdsecUrl = Optional.ofNullable(config.getString("crowdsecUrl", null))
            .filter(s -> !s.isEmpty())
            .map(Throwing.function(URL::new))
            .orElseThrow(() -> new IllegalArgumentException("Crowdsec's url is invalid."));

        String apiKey = Optional.of(config.getString("apiKey"))
            .orElseThrow(() -> new IllegalArgumentException("Missing apiKey!"));
        return new CrowdsecClientConfiguration(crowdsecUrl, apiKey);
    }

    public CrowdsecClientConfiguration(URL url, String apiKey) {
        this.url = url;
        this.apiKey = apiKey;
    }

    public URL getUrl() {
        return this.url;
    }

    public String getApiKey() {
        return this.apiKey;
    }
}
