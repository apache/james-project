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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.blob.api.BucketName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BucketNameResolverTest {
    @Nested
    class EmptyPrefix {
        @Test
        void resolveShouldReturnPassedValue() {
            BucketNameResolver resolver = BucketNameResolver.builder()
                .noPrefix()
                .namespace(BucketName.of("namespace"))
                .build();

            assertThat(resolver.resolve(BucketName.of("bucketName")))
                .isEqualTo(BucketName.of("bucketName"));
        }

        @Test
        void resolveShouldReturnValueWhenNamespace() {
            BucketNameResolver resolver = BucketNameResolver.builder()
                .noPrefix()
                .namespace(BucketName.of("namespace"))
                .build();

            assertThat(resolver.resolve(BucketName.of("namespace")))
                .isEqualTo(BucketName.of("namespace"));
        }
    }

    @Nested
    class EmptyNamespace {
        @Test
        void resolveShouldReturnPassedValueWithPrefix() {
            BucketNameResolver resolver = BucketNameResolver.builder()
                .prefix("bucketPrefix-")
                .noNamespace()
                .build();

            assertThat(resolver.resolve(BucketName.of("bucketName")))
                .isEqualTo(BucketName.of("bucketPrefix-bucketName"));
        }
    }

    @Nested
    class BothAreEmpty {
        @Test
        void resolveShouldReturnPassedValue() {
            BucketNameResolver resolver = BucketNameResolver.builder()
                .noPrefix()
                .noNamespace()
                .build();

            assertThat(resolver.resolve(BucketName.of("bucketName")))
                .isEqualTo(BucketName.of("bucketName"));
        }
    }

    @Nested
    class BothArePresent {
        @Test
        void resolveShouldReturnPassedValueWithPrefix() {
            BucketNameResolver resolver = BucketNameResolver.builder()
                .prefix("bucketPrefix-")
                .namespace(BucketName.of("namespace"))
                .build();

            assertThat(resolver.resolve(BucketName.of("bucketName")))
                .isEqualTo(BucketName.of("bucketPrefix-bucketName"));
        }

        @Test
        void resolveShouldReturnNamespaceWhenPassingNamespace() {
            BucketNameResolver resolver = BucketNameResolver.builder()
                .prefix("bucketPrefix-")
                .namespace(BucketName.of("namespace"))
                .build();

            assertThat(resolver.resolve(BucketName.of("namespace")))
                .isEqualTo(BucketName.of("namespace"));
        }
    }


    @Test
    void resolveShouldThrowWhenNullBucketName() {
        BucketNameResolver resolver = BucketNameResolver.builder()
            .noPrefix()
            .noNamespace()
            .build();

        assertThatThrownBy(() -> resolver.resolve(null))
            .isInstanceOf(NullPointerException.class);
    }
}
