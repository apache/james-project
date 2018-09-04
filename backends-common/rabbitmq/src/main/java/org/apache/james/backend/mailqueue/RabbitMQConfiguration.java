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

    private static final String URI_PROPERTY_NAME = "uri";

    public static RabbitMQConfiguration from(PropertiesConfiguration configuration) {
        String uriAsString = configuration.getString(URI_PROPERTY_NAME);
        Preconditions.checkState(!Strings.isNullOrEmpty(uriAsString), "You need to specify the URI of RabbitMQ");
        URI uri = checkURI(uriAsString);
        return new RabbitMQConfiguration(uri);
    }

    private static URI checkURI(String uri) {
        try {
            return URI.create(uri);
        } catch (Exception e) {
            throw new IllegalStateException("You need to specify a valid URI", e);
        }
    }
    private final URI uri;

    private RabbitMQConfiguration(URI uri) {
        this.uri = uri;
    }

    public URI getUri() {
        return uri;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof RabbitMQConfiguration) {
            RabbitMQConfiguration that = (RabbitMQConfiguration) o;

            return Objects.equals(this.uri, that.uri);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(uri);
    }
}
