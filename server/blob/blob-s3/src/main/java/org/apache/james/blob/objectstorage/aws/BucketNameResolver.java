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

import java.util.Optional;

import org.apache.james.blob.api.BucketName;

import com.google.common.base.Preconditions;
import org.apache.james.blob.api.ResolvedBucketName;

public class BucketNameResolver {
    static class Builder {

        @FunctionalInterface
        interface RequirePrefix {
            RequireNamespace prefix(Optional<String> prefix);

            default RequireNamespace noPrefix() {
                return prefix(Optional.empty());
            }

            default RequireNamespace prefix(String prefix) {
                return prefix(Optional.ofNullable(prefix));
            }
        }

        @FunctionalInterface
        interface RequireNamespace {
            ReadyToBuild namespace(Optional<BucketName> namespace);

            default ReadyToBuild namespace(BucketName namespace) {
                return namespace(Optional.ofNullable(namespace));
            }

            default ReadyToBuild noNamespace() {
                return namespace(Optional.empty());
            }
        }

        static final class ReadyToBuild {
            private final Optional<BucketName> namespace;
            private final Optional<String> prefix;

            ReadyToBuild(Optional<BucketName> namespace, Optional<String> prefix) {
                this.namespace = namespace;
                this.prefix = prefix;
            }

            BucketNameResolver build() {
                return new BucketNameResolver(namespace, prefix);
            }
        }
    }

    static Builder.RequirePrefix builder() {
        return prefix -> namespace -> new Builder.ReadyToBuild(namespace, prefix);
    }

    private final Optional<BucketName> namespace;
    private final Optional<String> prefix;

    private BucketNameResolver(Optional<BucketName> namespace, Optional<String> prefix) {
        Preconditions.checkNotNull(namespace);
        Preconditions.checkNotNull(prefix);

        this.namespace = namespace;
        this.prefix = prefix;
    }

    ResolvedBucketName resolve(BucketName bucketName) {
        Preconditions.checkNotNull(bucketName);

        if (isNameSpace(bucketName)) {
            return ResolvedBucketName.of(bucketName.asString());
        }
        return prefix
            .map(bucketPrefix -> ResolvedBucketName.of(bucketPrefix + bucketName.asString()))
            .orElse(ResolvedBucketName.of(bucketName.asString()));
    }

    Optional<BucketName> unresolve(ResolvedBucketName bucketName) {
        if (isNameSpace(bucketName)) {
            return Optional.of(BucketName.of(bucketName.asString()));
        }

        return prefix.map(p -> {
            if (bucketName.asString().startsWith(p)) {
                return Optional.of(BucketName.of(bucketName.asString().substring(p.length())));
            }
            return Optional.<BucketName>empty();
        }).orElse(Optional.of(BucketName.of(bucketName.asString())));
    }

    private boolean isNameSpace(BucketName bucketName) {
        return namespace
            .map(existingNamespace -> existingNamespace.asString().equals(bucketName.asString()))
            .orElse(false);
    }

    private boolean isNameSpace(ResolvedBucketName bucketName) {
        return namespace
            .map(existingNamespace -> existingNamespace.asString().equals(bucketName.asString()))
            .orElse(false);
    }
}
