/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.blob.objectstorage.aws;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class AwsS3ObjectStorageConfiguration {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String endpoint;
        private String accessKeyId;
        private String secretKey;

        private Builder() {}

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder accessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
            return this;
        }

        public Builder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        public AwsS3ObjectStorageConfiguration build() {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(endpoint), "'endpoint' is mandatory");
            Preconditions.checkArgument(!Strings.isNullOrEmpty(accessKeyId), "'accessKeyId' is mandatory");
            Preconditions.checkArgument(!Strings.isNullOrEmpty(secretKey), "'secretKey' is mandatory");
            return new AwsS3ObjectStorageConfiguration(endpoint, accessKeyId, secretKey);
        }
    }

    private final String endpoint;
    private final String accessKeyId;
    private final String secretKey;

    private AwsS3ObjectStorageConfiguration(String endpoint,
                          String accessKeyId,
                          String secretKey) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.secretKey = secretKey;
    }

    public String getEndpoint() {
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
        if (o instanceof AwsS3ObjectStorageConfiguration) {
            AwsS3ObjectStorageConfiguration that = (AwsS3ObjectStorageConfiguration) o;
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
