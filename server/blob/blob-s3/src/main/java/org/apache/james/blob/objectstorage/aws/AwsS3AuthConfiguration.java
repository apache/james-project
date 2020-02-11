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

package org.apache.james.blob.objectstorage.aws;

import java.net.URI;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class AwsS3AuthConfiguration {

    public static Builder.RequiredEndpoint builder() {
        return endpoint -> accessKeyId -> secretKey -> new Builder.ReadyToBuild(endpoint, accessKeyId, secretKey);
    }

    public interface Builder {

        @FunctionalInterface
        interface RequiredEndpoint {
            RequiredAccessKeyId endpoint(URI endpoint);
        }

        @FunctionalInterface
        interface RequiredAccessKeyId {
            RequiredSecretKey accessKeyId(String accessKeyId);
        }

        @FunctionalInterface
        interface RequiredSecretKey {
            ReadyToBuild secretKey(String secretKey);
        }

        class ReadyToBuild {
            private final URI endpoint;
            private final String accessKeyId;
            private final String secretKey;

            public ReadyToBuild(URI endpoint, String accessKeyId, String secretKey) {
                this.endpoint = endpoint;
                this.accessKeyId = accessKeyId;
                this.secretKey = secretKey;
            }

            public AwsS3AuthConfiguration build() {
                Preconditions.checkNotNull(endpoint, "'endpoint' is mandatory");

                Preconditions.checkNotNull(accessKeyId, "'accessKeyId' is mandatory");
                Preconditions.checkArgument(!accessKeyId.isEmpty(), "'accessKeyId' is mandatory");

                Preconditions.checkNotNull(secretKey, "'secretKey' is mandatory");
                Preconditions.checkArgument(!secretKey.isEmpty(), "'secretKey' is mandatory");

                return new AwsS3AuthConfiguration(endpoint, accessKeyId, secretKey);
            }
        }
    }

    private final URI endpoint;
    private final String accessKeyId;
    private final String secretKey;

    private AwsS3AuthConfiguration(URI endpoint,
                                   String accessKeyId,
                                   String secretKey) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.secretKey = secretKey;
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof AwsS3AuthConfiguration) {
            AwsS3AuthConfiguration that = (AwsS3AuthConfiguration) o;
            return Objects.equal(endpoint, that.endpoint) &&
                Objects.equal(accessKeyId, that.accessKeyId) &&
                Objects.equal(secretKey, that.secretKey);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(endpoint, accessKeyId, secretKey);
    }

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this)
            .add("endpoint", endpoint)
            .add("accessKeyId", accessKeyId)
            .add("secretKey", secretKey)
            .toString();
    }
}
