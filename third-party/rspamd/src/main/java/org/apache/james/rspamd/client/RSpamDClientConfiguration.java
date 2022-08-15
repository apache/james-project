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

public class RSpamDClientConfiguration {
    public static final Integer DEFAULT_TIMEOUT_IN_SECONDS = 15;

    private final URL url;
    private final String password;
    private final Optional<Integer> timeout;

    public RSpamDClientConfiguration(URL url, String password, Optional<Integer> timeout) {
        this.url = url;
        this.password = password;
        this.timeout = timeout;
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
}
