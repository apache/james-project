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
package org.apache.james.backend.mailqueue;

import java.net.URI;
import java.util.Objects;

import org.apache.commons.configuration.PropertiesConfiguration;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class RabbitMQConfiguration {
    @FunctionalInterface
    public interface RequireAmqpUri {
        RequireManagementUri amqpUri(URI amqpUri);
    }

    @FunctionalInterface
    public interface RequireManagementUri {
        Builder managementUri(URI managementUri);
    }

    public static class Builder {
        private final URI amqpUri;
        private final URI managementUri;

        private Builder(URI amqpUri, URI managementUri) {
            this.amqpUri = amqpUri;
            this.managementUri = managementUri;
        }

        public RabbitMQConfiguration build() {
            Preconditions.checkNotNull(amqpUri, "'amqpUri' should not be null");
            Preconditions.checkNotNull(managementUri, "'managementUri' should not be null");
            return new RabbitMQConfiguration(amqpUri, managementUri);
        }
    }

    private static final String URI_PROPERTY_NAME = "uri";
    private static final String MANAGEMENT_URI_PROPERTY_NAME = "management.uri";

    public static RequireAmqpUri builder() {
        return amqpUri -> managementUri -> new Builder(amqpUri, managementUri);
    }

    public static RabbitMQConfiguration from(PropertiesConfiguration configuration) {
        String uriAsString = configuration.getString(URI_PROPERTY_NAME);
        Preconditions.checkState(!Strings.isNullOrEmpty(uriAsString), "You need to specify the URI of RabbitMQ");
        URI amqpUri = checkURI(uriAsString);

        String managementUriAsString = configuration.getString(MANAGEMENT_URI_PROPERTY_NAME);
        Preconditions.checkState(!Strings.isNullOrEmpty(managementUriAsString), "You need to specify the management URI of RabbitMQ");
        URI managementUri = checkURI(managementUriAsString);

        return builder()
            .amqpUri(amqpUri)
            .managementUri(managementUri)
            .build();
    }

    private static URI checkURI(String uri) {
        try {
            return URI.create(uri);
        } catch (Exception e) {
            throw new IllegalStateException("You need to specify a valid URI", e);
        }
    }

    private final URI uri;
    private final URI managementUri;

    private RabbitMQConfiguration(URI uri, URI managementUri) {
        this.uri = uri;
        this.managementUri = managementUri;
    }

    public URI getUri() {
        return uri;
    }

    public URI getManagementUri() {
        return managementUri;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof RabbitMQConfiguration) {
            RabbitMQConfiguration that = (RabbitMQConfiguration) o;

            return Objects.equals(this.uri, that.uri)
                && Objects.equals(this.managementUri, that.managementUri);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(uri, managementUri);
    }
}
