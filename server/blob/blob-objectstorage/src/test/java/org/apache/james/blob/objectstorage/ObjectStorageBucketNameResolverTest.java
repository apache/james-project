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

package org.apache.james.blob.objectstorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.blob.api.BucketName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ObjectStorageBucketNameResolverTest {

    @Nested
    class EmptyPrefix {

        @Test
        void resolveShouldReturnPassedValue() {
            ObjectStorageBucketNameResolver resolver = ObjectStorageBucketNameResolver.builder()
                .noPrefix()
                .namespace(BucketName.of("namespace"))
                .build();

            assertThat(resolver.resolve(BucketName.of("bucketName")))
                .isEqualTo(ObjectStorageBucketName.of("bucketName"));
        }

        @Test
        void resolveShouldReturnValueWhenNamespace() {
            ObjectStorageBucketNameResolver resolver = ObjectStorageBucketNameResolver.builder()
                .noPrefix()
                .namespace(BucketName.of("namespace"))
                .build();

            assertThat(resolver.resolve(BucketName.of("namespace")))
                .isEqualTo(ObjectStorageBucketName.of("namespace"));
        }
    }

    @Nested
    class EmptyNamespace {

        @Test
        void resolveShouldReturnPassedValueWithPrefix() {
            ObjectStorageBucketNameResolver resolver = ObjectStorageBucketNameResolver.builder()
                .prefix("prefix-")
                .noNamespace()
                .build();

            assertThat(resolver.resolve(BucketName.of("bucketName")))
                .isEqualTo(ObjectStorageBucketName.of("prefix-bucketName"));
        }
    }

    @Nested
    class BothAreEmpty {

        @Test
        void resolveShouldReturnPassedValue() {
            ObjectStorageBucketNameResolver resolver = ObjectStorageBucketNameResolver.builder()
                .noPrefix()
                .noNamespace()
                .build();

            assertThat(resolver.resolve(BucketName.of("bucketName")))
                .isEqualTo(ObjectStorageBucketName.of("bucketName"));
        }
    }

    @Nested
    class BothArePresent {

        @Test
        void resolveShouldReturnPassedValueWithPrefix() {
            ObjectStorageBucketNameResolver resolver = ObjectStorageBucketNameResolver.builder()
                .prefix("prefix-")
                .namespace(BucketName.of("namespace"))
                .build();

            assertThat(resolver.resolve(BucketName.of("bucketName")))
                .isEqualTo(ObjectStorageBucketName.of("prefix-bucketName"));
        }

        @Test
        void resolveShouldReturnNamespaceWhenPassingNamespace() {
            ObjectStorageBucketNameResolver resolver = ObjectStorageBucketNameResolver.builder()
                .prefix("prefix-")
                .namespace(BucketName.of("namespace"))
                .build();

            assertThat(resolver.resolve(BucketName.of("namespace")))
                .isEqualTo(ObjectStorageBucketName.of("namespace"));
        }
    }


    @Test
    void resolveShouldThrowWhenNullBucketName() {
        ObjectStorageBucketNameResolver resolver = ObjectStorageBucketNameResolver.builder()
            .noPrefix()
            .noNamespace()
            .build();

        assertThatThrownBy(() -> resolver.resolve(null))
            .isInstanceOf(NullPointerException.class);
    }
}