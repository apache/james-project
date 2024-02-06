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

package org.apache.james.blob.api;

import static org.apache.james.blob.api.BlobStoreDAOFixture.ELEVEN_KILOBYTES;
import static org.apache.james.blob.api.BlobStoreDAOFixture.EMPTY_BYTEARRAY;
import static org.apache.james.blob.api.BlobStoreDAOFixture.OTHER_TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.SHORT_BYTEARRAY;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TWELVE_MEGABYTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReadSaveBlobStoreDAOContract {

    BlobStoreDAO testee();

    @Test
    default void saveShouldThrowWhenNullData() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, (byte[]) null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveShouldThrowWhenNullString() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, (String) null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveShouldThrowWhenNullInputStream() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, (InputStream) null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveShouldThrowWhenNullByteSource() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, (ByteSource) null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveShouldSaveEmptyData() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, EMPTY_BYTEARRAY)).block();
        byte[] bytes = Mono.from(store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(bytes).isEmpty();
    }

    @Test
    default void saveShouldSaveEmptyString() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, "")).block();

        byte[] bytes = Mono.from(store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    default void saveShouldSaveEmptyInputStream() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteArrayInputStream(EMPTY_BYTEARRAY))).block();

        byte[] bytes = Mono.from(store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(bytes).isEmpty();
    }

    @Test
    default void saveShouldSaveEmptyByteSource() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.empty())).block();

        byte[] bytes = Mono.from(store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(bytes).isEmpty();
    }

    @Test
    default void readBytesShouldThrowWhenNotExisting() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.readBytes(TEST_BUCKET_NAME, new TestBlobId("unknown"))).block())
            .isExactlyInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    default void readBytesShouldReturnSavedData() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        byte[] bytes = Mono.from(store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(bytes).isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    default void readBytesShouldReturnLongSavedData() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ELEVEN_KILOBYTES)).block();

        byte[] bytes = Mono.from(store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(bytes).isEqualTo(ELEVEN_KILOBYTES);
    }

    @Test
    default void readBytesShouldReturnBigSavedData() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES)).block();

        byte[] bytes = Mono.from(store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(bytes).isEqualTo(TWELVE_MEGABYTES);
    }

    @Test
    default void readStreamShouldThrowWhenNotExisting() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> store.read(TEST_BUCKET_NAME, new TestBlobId("unknown")).read())
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    default void saveShouldCreateBucket() {
        BlobStoreDAO store = testee();
        BucketName nonExisting = BucketName.of("non-existing-bucket");
        Mono.from(store.save(nonExisting, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        //read for a non-existing bucket would throw
        assertThatCode(() -> store.read(nonExisting, TEST_BLOB_ID))
            .doesNotThrowAnyException();
    }

    @Test
    default void readShouldReturnSavedData() {
        BlobStoreDAO store = testee();
        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        InputStream read = store.read(TEST_BUCKET_NAME, TEST_BLOB_ID);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    default void readShouldReturnLongSavedData() {
        BlobStoreDAO store = testee();
        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ELEVEN_KILOBYTES)).block();

        InputStream read = store.read(TEST_BUCKET_NAME, TEST_BLOB_ID);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(ELEVEN_KILOBYTES));
    }

    @Test
    default void readShouldReturnBigSavedData() {
        BlobStoreDAO store = testee();
        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES)).block();

        InputStream read = store.read(TEST_BUCKET_NAME, TEST_BLOB_ID);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(TWELVE_MEGABYTES));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("blobs")
    default void saveBytesShouldBeIdempotent(String description, byte[] bytes) {
        BlobStoreDAO store = testee();
        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, bytes)).block();
        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, bytes)).block();

        byte[] read = Mono.from(store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(read).isEqualTo(bytes);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("blobs")
    default void saveByteSourceShouldBeIdempotent(String description, byte[] bytes) {
        BlobStoreDAO store = testee();
        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES)).block();
        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(bytes))).block();

        byte[] read = Mono.from(store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(read).isEqualTo(bytes);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("blobs")
    default void saveInputStreamShouldBeIdempotent(String description, byte[] bytes) {
        BlobStoreDAO store = testee();
        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES)).block();
        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteArrayInputStream(bytes))).block();

        byte[] read = Mono.from(store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(read).isEqualTo(bytes);
    }

    @Test
    default void saveInputStreamShouldNotOverwritePreviousDataOnFailingInputStream() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(ELEVEN_KILOBYTES))).block();
        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, getThrowingInputStream()))
            .onErrorResume(throwable -> Mono.empty()).block();

        byte[] read = Mono.from(store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(read).isEqualTo(ELEVEN_KILOBYTES);
    }

    @Test
    default void saveByteSourceShouldNotOverwritePreviousDataOnFailingInputStream() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(ELEVEN_KILOBYTES))).block();
        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                return getThrowingInputStream();
            }
        }))
            .onErrorResume(throwable -> Mono.empty()).block();

        byte[] read = Mono.from(store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(read).isEqualTo(ELEVEN_KILOBYTES);
    }

    @Test
    default void saveByteSourceShouldThrowOnIOException() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteSource() {
                @Override
                public InputStream openStream() throws IOException {
                    return getThrowingInputStream();
                }
            })).block())
        .isInstanceOf(ObjectStoreIOException.class);
    }

    @Test
    default void saveInputStreamShouldThrowOnIOException() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, getThrowingInputStream())).block())
            .isInstanceOf(ObjectStoreIOException.class);
    }

    @Test
    default void listShouldReturnEmptyByDefault() {
        assertThat(Flux.from(testee().listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
    }

    @Test
    default void listShouldReturnPresentBlobs() {
        BlobStoreDAO store = testee();
        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(ELEVEN_KILOBYTES))).block();
        Mono.from(store.save(TEST_BUCKET_NAME, OTHER_TEST_BLOB_ID, ByteSource.wrap(ELEVEN_KILOBYTES))).block();

        assertThat(Flux.from(testee().listBlobs(TEST_BUCKET_NAME))
            .map(BlobId::asString)
            .collectList()
            .block())
            .containsOnly(TEST_BLOB_ID.asString(), OTHER_TEST_BLOB_ID.asString());
    }

    static Stream<Arguments> blobs() {
        return Stream.of(new Object[]{"SHORT", SHORT_BYTEARRAY}, new Object[]{"LONG", ELEVEN_KILOBYTES}, new Object[]{"BIG", TWELVE_MEGABYTES})
            .map(Arguments::of);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource(value = "blobs")
    default void concurrentSaveBytesShouldReturnConsistentValues(String description, byte[] bytes) throws ExecutionException, InterruptedException {
        Mono.from(testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, bytes)).block();
        ConcurrentTestRunner.builder()
            .randomlyDistributedReactorOperations(
                (threadNumber, step) -> testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, bytes),
                (threadNumber, step) -> checkConcurrentSaveOperation(bytes)
            )
            .threadCount(10)
            .operationCount(20)
            .runSuccessfullyWithin(Duration.ofMinutes(10));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("blobs")
    default void concurrentSaveInputStreamShouldReturnConsistentValues(String description, byte[] bytes) throws ExecutionException, InterruptedException {
        Mono.from(testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, bytes)).block();
        ConcurrentTestRunner.builder()
            .randomlyDistributedReactorOperations(
                (threadNumber, step) -> testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteArrayInputStream(bytes)),
                (threadNumber, step) -> checkConcurrentSaveOperation(bytes)
            )
            .threadCount(10)
            .operationCount(20)
            .runSuccessfullyWithin(Duration.ofMinutes(2));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("blobs")
    default void concurrentSaveByteSourceShouldReturnConsistentValues(String description, byte[] bytes) throws ExecutionException, InterruptedException {
        Mono.from(testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, bytes)).block();
        ConcurrentTestRunner.builder()
            .randomlyDistributedReactorOperations(
                (threadNumber, step) -> testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(bytes)),
                (threadNumber, step) -> checkConcurrentSaveOperation(bytes)
            )
            .threadCount(10)
            .operationCount(20)
            .runSuccessfullyWithin(Duration.ofMinutes(2));
    }

    default Mono<Void> checkConcurrentSaveOperation(byte[] expected) {
        return Mono.from(testee().readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID))
            //assertj is very cpu-intensive, let's compute the assertion only when arrays are different
            .filter(bytes -> !Arrays.equals(bytes, expected))
            .doOnNext(bytes -> assertThat(bytes).isEqualTo(expected))
            .then();
    }

    default FilterInputStream getThrowingInputStream() {
        return new FilterInputStream(new ByteArrayInputStream(TWELVE_MEGABYTES)) {
            int failingThreshold = 5;
            int alreadyRead = 0;

            @Override
            public int read() throws IOException {
                if (alreadyRead < failingThreshold) {
                    alreadyRead++;
                    return super.read();
                } else {
                    throw new IOException("error on read");
                }
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int remaining = b.length - off;
                int toRead = Math.min(remaining, len);
                for (int i = 0; i < toRead; i++) {
                    int value = read();
                    if (value != -1) {
                        b[off] = (byte) value;
                    }
                }
                return toRead;
            }

        };
    }

}
