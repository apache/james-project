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

package org.apache.james.linshare;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class LinshareConfiguration {

    public static class Builder {

        @FunctionalInterface
        public interface RequireUrl {
            RequireAuthorizationToken url(URL url);

            default RequireAuthorizationToken urlAsString(String url) throws MalformedURLException {
                return url(new URL(url));
            }
        }

        public interface RequireAuthorizationToken {
            ReadyToBuild authorizationToken(AuthorizationToken token);
        }

        public static class ReadyToBuild {
            private final URL url;
            private final AuthorizationToken token;

            ReadyToBuild(URL url, AuthorizationToken token) {
                this.url = url;
                this.token = token;
            }

            public LinshareConfiguration build() {
                return new LinshareConfiguration(url, token);
            }
        }
    }

    public static Builder.RequireUrl builder() {
        return url -> credential -> new Builder.ReadyToBuild(url, credential);
    }

    private final URL url;
    private final AuthorizationToken token;

    @VisibleForTesting
    LinshareConfiguration(URL url, AuthorizationToken token) {
        Preconditions.checkNotNull(url);
        Preconditions.checkNotNull(token);

        this.url = url;
        this.token = token;
    }

    public URL getUrl() {
        return url;
    }

    public AuthorizationToken getToken() {
        return token;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof LinshareConfiguration) {
            LinshareConfiguration that = (LinshareConfiguration) o;

            return Objects.equals(this.url, that.url)
                && Objects.equals(this.token, that.token);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(url, token);
    }
}
