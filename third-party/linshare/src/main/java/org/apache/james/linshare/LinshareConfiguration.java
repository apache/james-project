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
import java.net.URI;
import java.net.URL;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.configuration2.Configuration;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class LinshareConfiguration {

    public static final String URL_PROPERTY = "blob.export.linshare.url";
    public static final String UUID_PROPERTY = "blob.export.linshare.technical.account.uuid";
    public static final String PASSWORD_PROPERTY = "blob.export.linshare.technical.account.password";

    public static class Builder {
        @FunctionalInterface
        public interface RequireUrl {
            RequireBasicAuthorization url(URL url);

            default RequireBasicAuthorization urlAsString(String url) throws MalformedURLException {
                if (url == null) {
                    throw new MalformedURLException("url can not be null");
                }
                return url(Throwing.supplier(() -> new URI(url).toURL()).get());
            }
        }

        public interface RequireBasicAuthorization {
            ReadyToBuild basicAuthorization(String uuid, String password);
        }

        public static class ReadyToBuild {
            private final URL url;
            private final UUID uuid;
            private final String password;

            ReadyToBuild(URL url, UUID uuid, String password) {
                this.url = url;
                this.uuid = uuid;
                this.password = password;
            }

            public LinshareConfiguration build() {
                return new LinshareConfiguration(url, uuid, password);
            }
        }
    }

    public static Builder.RequireUrl builder() {
        return url -> (uuid, password) -> new Builder.ReadyToBuild(url, UUID.fromString(uuid), password);
    }

    public static LinshareConfiguration from(Configuration configuration) throws MalformedURLException {
        return builder()
            .urlAsString(configuration.getString(URL_PROPERTY, null))
            .basicAuthorization(
                configuration.getString(UUID_PROPERTY),
                configuration.getString(PASSWORD_PROPERTY))
            .build();
    }

    private final URL url;
    private final UUID uuid;
    private final String password;

    @VisibleForTesting
    LinshareConfiguration(URL url, UUID uuid, String password) {
        Preconditions.checkNotNull(url, "'%s' can not be null", URL_PROPERTY);
        Preconditions.checkNotNull(uuid, "'%s' can not be null", UUID_PROPERTY);

        Preconditions.checkNotNull(password, "'%s' can not be null", PASSWORD_PROPERTY);
        Preconditions.checkArgument(!password.isEmpty(), "'%s' can not be empty", PASSWORD_PROPERTY);

        this.url = url;
        this.uuid = uuid;
        this.password = password;
    }

    public URL getUrl() {
        return url;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof LinshareConfiguration) {
            LinshareConfiguration that = (LinshareConfiguration) o;

            return Objects.equals(this.url, that.url)
                && Objects.equals(this.uuid, that.uuid)
                && Objects.equals(this.password, that.password);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(url, uuid, password);
    }
}
