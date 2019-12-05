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
package org.apache.james.mailbox.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class BatchSizesTest {

    @Test
    void shouldRespectJavaBeanContract() {
        EqualsVerifier.forClass(BatchSizes.class).verify();
    }

    @Test
    void defaultValuesShouldReturnDefaultForEachParameters() {
        BatchSizes batchSizes = BatchSizes.defaultValues();
        assertThat(batchSizes.getFetchMetadata()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
        assertThat(batchSizes.getFetchHeaders()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
        assertThat(batchSizes.getFetchBody()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
        assertThat(batchSizes.getFetchFull()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
        assertThat(batchSizes.getCopyBatchSize()).isEmpty();
        assertThat(batchSizes.getMoveBatchSize()).isEmpty();
    }

    @Test
    void uniqueBatchSizeShouldSetTheSameValueToAllAttributes() {
        int batchSize = 10;
        BatchSizes batchSizes = BatchSizes.uniqueBatchSize(batchSize);
        assertThat(batchSizes.getFetchMetadata()).isEqualTo(batchSize);
        assertThat(batchSizes.getFetchHeaders()).isEqualTo(batchSize);
        assertThat(batchSizes.getFetchBody()).isEqualTo(batchSize);
        assertThat(batchSizes.getFetchFull()).isEqualTo(batchSize);
        assertThat(batchSizes.getCopyBatchSize()).contains(batchSize);
        assertThat(batchSizes.getMoveBatchSize()).contains(batchSize);
    }

    @Test
    void fetchMetadataShouldThrowWhenNegative() {
        assertThatThrownBy(() -> BatchSizes.builder()
                .fetchMetadata(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fetchMetadataShouldThrowWhenZero() {
        assertThatThrownBy(() -> BatchSizes.builder()
                .fetchMetadata(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fetchHeadersShouldThrowWhenNegative() {
        assertThatThrownBy(() -> BatchSizes.builder()
                .fetchHeaders(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fetchHeadersShouldThrowWhenZero() {
        assertThatThrownBy(() -> BatchSizes.builder()
                .fetchHeaders(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fetchBodyShouldThrowWhenNegative() {
        assertThatThrownBy(() -> BatchSizes.builder()
                .fetchBody(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fetchBodyShouldThrowWhenZero() {
        assertThatThrownBy(() -> BatchSizes.builder()
                .fetchBody(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fetchFullShouldThrowWhenNegative() {
        assertThatThrownBy(() -> BatchSizes.builder()
                .fetchFull(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fetchFullShouldThrowWhenZero() {
        assertThatThrownBy(() -> BatchSizes.builder()
                .fetchFull(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copyBatchSizeShouldThrowWhenNegative() {
        assertThatThrownBy(() -> BatchSizes.builder()
                .copyBatchSize(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copyBatchSizeShouldThrowWhenZero() {
        assertThatThrownBy(() -> BatchSizes.builder()
                .copyBatchSize(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moveBatchSizeShouldThrowWhenNegative() {
        assertThatThrownBy(() -> BatchSizes.builder()
                .moveBatchSize(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moveBatchSizeShouldThrowWhenZero() {
        assertThatThrownBy(() -> BatchSizes.builder()
                .moveBatchSize(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildShouldSetDefaultValueToFetchMetadataWhenNotGiven() {
        BatchSizes batchSizes = BatchSizes.builder()
                .build();
        assertThat(batchSizes.getFetchMetadata()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
    }

    @Test
    void buildShouldSetValueToFetchMetadataWhenGiven() {
        int expected = 123;
        BatchSizes batchSizes = BatchSizes.builder()
                .fetchMetadata(expected)
                .build();
        assertThat(batchSizes.getFetchMetadata()).isEqualTo(expected);
    }

    @Test
    void buildShouldSetDefaultValueToFetchHeadersWhenNotGiven() {
        BatchSizes batchSizes = BatchSizes.builder()
                .build();
        assertThat(batchSizes.getFetchHeaders()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
    }

    @Test
    void buildShouldSetValueToFetchHeadersWhenGiven() {
        int expected = 123;
        BatchSizes batchSizes = BatchSizes.builder()
                .fetchHeaders(expected)
                .build();
        assertThat(batchSizes.getFetchHeaders()).isEqualTo(expected);
    }

    @Test
    void buildShouldSetDefaultValueToFetchBodyWhenNotGiven() {
        BatchSizes batchSizes = BatchSizes.builder()
                .build();
        assertThat(batchSizes.getFetchBody()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
    }

    @Test
    void buildShouldSetValueToFetchBodyWhenGiven() {
        int expected = 123;
        BatchSizes batchSizes = BatchSizes.builder()
                .fetchBody(expected)
                .build();
        assertThat(batchSizes.getFetchBody()).isEqualTo(expected);
    }

    @Test
    void buildShouldSetDefaultValueToFetchFullWhenNotGiven() {
        BatchSizes batchSizes = BatchSizes.builder()
                .build();
        assertThat(batchSizes.getFetchFull()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
    }

    @Test
    void buildShouldSetValueToFetchFullWhenGiven() {
        int expected = 123;
        BatchSizes batchSizes = BatchSizes.builder()
                .fetchFull(expected)
                .build();
        assertThat(batchSizes.getFetchFull()).isEqualTo(expected);
    }

    @Test
    void buildShouldSetDefaultValueToCopyBatchSizeWhenNotGiven() {
        BatchSizes batchSizes = BatchSizes.builder()
                .build();
        assertThat(batchSizes.getCopyBatchSize()).isEmpty();
    }

    @Test
    void buildShouldSetValueToCopyBatchSizeWhenGiven() {
        int expected = 123;
        BatchSizes batchSizes = BatchSizes.builder()
                .copyBatchSize(expected)
                .build();
        assertThat(batchSizes.getCopyBatchSize()).contains(expected);
    }

    @Test
    void buildShouldSetDefaultValueToMoveBatchSizeWhenNotGiven() {
        BatchSizes batchSizes = BatchSizes.builder()
                .build();
        assertThat(batchSizes.getMoveBatchSize()).isEmpty();
    }

    @Test
    void buildShouldSetValueToMoveBatchSizeWhenGiven() {
        int expected = 123;
        BatchSizes batchSizes = BatchSizes.builder()
                .moveBatchSize(expected)
                .build();
        assertThat(batchSizes.getMoveBatchSize()).contains(expected);
    }
}
