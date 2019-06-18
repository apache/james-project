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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import nl.jqno.equalsverifier.EqualsVerifier;

public class BatchSizesTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldRespectJavaBeanContract() {
        EqualsVerifier.forClass(BatchSizes.class).verify();
    }

    @Test
    public void defaultValuesShouldReturnDefaultForEachParameters() {
        BatchSizes batchSizes = BatchSizes.defaultValues();
        assertThat(batchSizes.getFetchMetadata()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
        assertThat(batchSizes.getFetchHeaders()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
        assertThat(batchSizes.getFetchBody()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
        assertThat(batchSizes.getFetchFull()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
        assertThat(batchSizes.getCopyBatchSize()).isEmpty();
        assertThat(batchSizes.getMoveBatchSize()).isEmpty();
    }

    @Test
    public void uniqueBatchSizeShouldSetTheSameValueToAllAttributes() {
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
    public void fetchMetadataShouldThrowWhenNegative() {
        expectedException.expect(IllegalArgumentException.class);

        BatchSizes.builder()
            .fetchMetadata(-1);
    }

    @Test
    public void fetchMetadataShouldThrowWhenZero() {
        expectedException.expect(IllegalArgumentException.class);

        BatchSizes.builder()
            .fetchMetadata(0);
    }

    @Test
    public void fetchHeadersShouldThrowWhenNegative() {
        expectedException.expect(IllegalArgumentException.class);

        BatchSizes.builder()
            .fetchHeaders(-1);
    }

    @Test
    public void fetchHeadersShouldThrowWhenZero() {
        expectedException.expect(IllegalArgumentException.class);

        BatchSizes.builder()
            .fetchHeaders(0);
    }

    @Test
    public void fetchBodyShouldThrowWhenNegative() {
        expectedException.expect(IllegalArgumentException.class);

        BatchSizes.builder()
            .fetchBody(-1);
    }

    @Test
    public void fetchBodyShouldThrowWhenZero() {
        expectedException.expect(IllegalArgumentException.class);

        BatchSizes.builder()
            .fetchBody(0);
    }

    @Test
    public void fetchFullShouldThrowWhenNegative() {
        expectedException.expect(IllegalArgumentException.class);

        BatchSizes.builder()
            .fetchFull(-1);
    }

    @Test
    public void fetchFullShouldThrowWhenZero() {
        expectedException.expect(IllegalArgumentException.class);

        BatchSizes.builder()
            .fetchFull(0);
    }

    @Test
    public void copyBatchSizeShouldThrowWhenNegative() {
        expectedException.expect(IllegalArgumentException.class);

        BatchSizes.builder()
            .copyBatchSize(-1);
    }

    @Test
    public void copyBatchSizeShouldThrowWhenZero() {
        expectedException.expect(IllegalArgumentException.class);

        BatchSizes.builder()
            .copyBatchSize(0);
    }

    @Test
    public void moveBatchSizeShouldThrowWhenNegative() {
        expectedException.expect(IllegalArgumentException.class);

        BatchSizes.builder()
            .moveBatchSize(-1);
    }

    @Test
    public void moveBatchSizeShouldThrowWhenZero() {
        expectedException.expect(IllegalArgumentException.class);

        BatchSizes.builder()
            .moveBatchSize(0);
    }

    @Test
    public void buildShouldSetDefaultValueToFetchMetadataWhenNotGiven() {
        BatchSizes batchSizes = BatchSizes.builder()
                .build();
        assertThat(batchSizes.getFetchMetadata()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
    }

    @Test
    public void buildShouldSetValueToFetchMetadataWhenGiven() {
        int expected = 123;
        BatchSizes batchSizes = BatchSizes.builder()
                .fetchMetadata(expected)
                .build();
        assertThat(batchSizes.getFetchMetadata()).isEqualTo(expected);
    }

    @Test
    public void buildShouldSetDefaultValueToFetchHeadersWhenNotGiven() {
        BatchSizes batchSizes = BatchSizes.builder()
                .build();
        assertThat(batchSizes.getFetchHeaders()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
    }

    @Test
    public void buildShouldSetValueToFetchHeadersWhenGiven() {
        int expected = 123;
        BatchSizes batchSizes = BatchSizes.builder()
                .fetchHeaders(expected)
                .build();
        assertThat(batchSizes.getFetchHeaders()).isEqualTo(expected);
    }

    @Test
    public void buildShouldSetDefaultValueToFetchBodyWhenNotGiven() {
        BatchSizes batchSizes = BatchSizes.builder()
                .build();
        assertThat(batchSizes.getFetchBody()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
    }

    @Test
    public void buildShouldSetValueToFetchBodyWhenGiven() {
        int expected = 123;
        BatchSizes batchSizes = BatchSizes.builder()
                .fetchBody(expected)
                .build();
        assertThat(batchSizes.getFetchBody()).isEqualTo(expected);
    }

    @Test
    public void buildShouldSetDefaultValueToFetchFullWhenNotGiven() {
        BatchSizes batchSizes = BatchSizes.builder()
                .build();
        assertThat(batchSizes.getFetchFull()).isEqualTo(BatchSizes.DEFAULT_BATCH_SIZE);
    }

    @Test
    public void buildShouldSetValueToFetchFullWhenGiven() {
        int expected = 123;
        BatchSizes batchSizes = BatchSizes.builder()
                .fetchFull(expected)
                .build();
        assertThat(batchSizes.getFetchFull()).isEqualTo(expected);
    }

    @Test
    public void buildShouldSetDefaultValueToCopyBatchSizeWhenNotGiven() {
        BatchSizes batchSizes = BatchSizes.builder()
                .build();
        assertThat(batchSizes.getCopyBatchSize()).isEmpty();
    }

    @Test
    public void buildShouldSetValueToCopyBatchSizeWhenGiven() {
        int expected = 123;
        BatchSizes batchSizes = BatchSizes.builder()
                .copyBatchSize(expected)
                .build();
        assertThat(batchSizes.getCopyBatchSize()).contains(expected);
    }

    @Test
    public void buildShouldSetDefaultValueToMoveBatchSizeWhenNotGiven() {
        BatchSizes batchSizes = BatchSizes.builder()
                .build();
        assertThat(batchSizes.getMoveBatchSize()).isEmpty();
    }

    @Test
    public void buildShouldSetValueToMoveBatchSizeWhenGiven() {
        int expected = 123;
        BatchSizes batchSizes = BatchSizes.builder()
                .moveBatchSize(expected)
                .build();
        assertThat(batchSizes.getMoveBatchSize()).contains(expected);
    }
}
