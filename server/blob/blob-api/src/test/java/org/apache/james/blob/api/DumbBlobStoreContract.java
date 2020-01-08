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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import org.apache.commons.io.IOUtils;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import com.google.common.base.Strings;
import com.google.common.io.ByteSource;
import reactor.core.publisher.Mono;

public interface DumbBlobStoreContract {

    BucketName TEST_BUCKET_NAME = BucketName.of("my-test-bucket");
    BlobId TEST_BLOB_ID = new TestBlobId("test-blob-id");
    String SHORT_STRING = "toto";
    byte[] EMPTY_BYTEARRAY = {};
    byte[] SHORT_BYTEARRAY = SHORT_STRING.getBytes(StandardCharsets.UTF_8);
    byte[] ELEVEN_KILOBYTES = Strings.repeat("0123456789\n", 1000).getBytes(StandardCharsets.UTF_8);
    byte[] TWELVE_MEGABYTES = Strings.repeat("0123456789\r\n", 1024 * 1024).getBytes(StandardCharsets.UTF_8);

    DumbBlobStore testee();

    @Test
    default void saveShouldThrowWhenNullData() {
        DumbBlobStore store = testee();

        assertThatThrownBy(() -> store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, (byte[]) null).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveShouldThrowWhenNullString() {
        DumbBlobStore store = testee();

        assertThatThrownBy(() -> store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, (String) null).block())
            .isInstanceOf(NullPointerException.class);
    }


    @Test
    default void saveShouldThrowWhenNullInputStream() {
        DumbBlobStore store = testee();

        assertThatThrownBy(() -> store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, (InputStream) null).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveShouldSaveEmptyData() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, EMPTY_BYTEARRAY).block();
        byte[] bytes = store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        assertThat(bytes).isEmpty();
    }

    @Test
    default void saveShouldSaveEmptyString() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, "").block();

        byte[] bytes = store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    default void saveShouldSaveEmptyInputStream() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteArrayInputStream(EMPTY_BYTEARRAY)).block();

        byte[] bytes = store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        assertThat(bytes).isEmpty();
    }

    @Test
    default void saveShouldSaveEmptyByteSource() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.empty()).block();

        byte[] bytes = store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        assertThat(bytes).isEmpty();
    }

    @Test
    default void readBytesShouldThrowWhenNotExisting() {
        DumbBlobStore store = testee();

        assertThatThrownBy(() -> store.readBytes(TEST_BUCKET_NAME, new TestBlobId("unknown")).block())
            .isExactlyInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    default void readBytesShouldReturnSavedData() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();

        byte[] bytes = store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        assertThat(bytes).isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    default void readBytesShouldReturnLongSavedData() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ELEVEN_KILOBYTES).block();

        byte[] bytes = store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        assertThat(bytes).isEqualTo(ELEVEN_KILOBYTES);
    }

    @Test
    default void readBytesShouldReturnBigSavedData() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES).block();

        byte[] bytes = store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        assertThat(bytes).isEqualTo(TWELVE_MEGABYTES);
    }

    @Test
    default void readShouldThrowWhenNotExistingStream() {
        DumbBlobStore store = testee();

        assertThatThrownBy(() -> store.read(TEST_BUCKET_NAME, new TestBlobId("unknown")))
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    default void saveShouldCreateBucket() {
        DumbBlobStore store = testee();
        BucketName nonExisting = BucketName.of("non-existing-bucket");
        store.save(nonExisting, TEST_BLOB_ID, SHORT_BYTEARRAY).block();

        //read for a non-existing bucket would throw
        assertThatCode(() -> store.read(nonExisting, TEST_BLOB_ID))
            .doesNotThrowAnyException();
    }

    @Test
    default void readShouldReturnSavedData() {
        DumbBlobStore store = testee();
        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();

        InputStream read = store.read(TEST_BUCKET_NAME, TEST_BLOB_ID);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    default void readShouldReturnLongSavedData() {
        DumbBlobStore store = testee();
        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ELEVEN_KILOBYTES).block();

        InputStream read = store.read(TEST_BUCKET_NAME, TEST_BLOB_ID);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(ELEVEN_KILOBYTES));
    }

    @Test
    default void readShouldReturnBigSavedData() {
        DumbBlobStore store = testee();
        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES).block();

        InputStream read = store.read(TEST_BUCKET_NAME, TEST_BLOB_ID);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(TWELVE_MEGABYTES));
    }

    @Test
    default void saveBytesShouldOverwritePreviousData() {
        DumbBlobStore store = testee();
        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ELEVEN_KILOBYTES).block();
        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();

        byte[] read = store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        assertThat(read).isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    default void saveByteSourceShouldOverwritePreviousData() {
        DumbBlobStore store = testee();
        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(ELEVEN_KILOBYTES)).block();
        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY)).block();

        byte[] read = store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        assertThat(read).isEqualTo(SHORT_BYTEARRAY);
    }


    @Test
    default void saveInputStreamShouldOverwritePreviousData() {
        DumbBlobStore store = testee();
        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(ELEVEN_KILOBYTES)).block();
        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteArrayInputStream(SHORT_BYTEARRAY)).block();

        byte[] read = store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        assertThat(read).isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    default void saveInputStreamShouldNotOverwritePreviousDataOnFailingInputStream() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(ELEVEN_KILOBYTES)).block();
        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, getThrowingInputStream())
            .onErrorResume(throwable -> Mono.empty())
            .block();

        byte[] read = store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        assertThat(read).isEqualTo(ELEVEN_KILOBYTES);
    }

    @Test
    default void saveByteSourceShouldNotOverwritePreviousDataOnFailingInputStream() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(ELEVEN_KILOBYTES)).block();
        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                return getThrowingInputStream();
            }
        })
            .onErrorResume(throwable -> Mono.empty())
            .block();

        byte[] read = store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        assertThat(read).isEqualTo(ELEVEN_KILOBYTES);
    }

    @Test
    default void saveByteSourceShouldThrowOnIOException() {
        DumbBlobStore store = testee();

        assertThatThrownBy(() -> store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteSource() {
                @Override
                public InputStream openStream() throws IOException {
                    return getThrowingInputStream();
                }
            })
            .block())
        .isInstanceOf(IOObjectStoreException.class);
    }

    @Test
    default void saveInputStreamShouldThrowOnIOException() {
        DumbBlobStore store = testee();

        assertThatThrownBy(() -> store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, getThrowingInputStream())
            .block())
            .isInstanceOf(IOObjectStoreException.class);
    }

    @Test
    default void concurrentSaveBytesShouldReturnConsistentValues() throws ExecutionException, InterruptedException {
        testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();
        ConcurrentTestRunner.builder()
            .reactorOperation((thread, iteration) -> getConcurrentOperation(bytes -> testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, bytes)))
            .threadCount(10)
            .operationCount(100)
            .runSuccessfullyWithin(Duration.ofMinutes(2));
    }

    @Test
    default void concurrentSaveInputStreamShouldReturnConsistentValues() throws ExecutionException, InterruptedException {
        testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();
        ConcurrentTestRunner.builder()
            .reactorOperation((thread, iteration) -> getConcurrentOperation(bytes -> testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteArrayInputStream(bytes))))
            .threadCount(10)
            .operationCount(100)
            .runSuccessfullyWithin(Duration.ofMinutes(2));
    }

    @Test
    default void concurrentSaveByteSourceShouldReturnConsistentValues() throws ExecutionException, InterruptedException {
        testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();
        ConcurrentTestRunner.builder()
            .reactorOperation((thread, iteration) -> getConcurrentOperation(bytes -> testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(bytes))))
            .threadCount(10)
            .operationCount(100)
            .runSuccessfullyWithin(Duration.ofMinutes(2));
    }

    default Publisher<Void> getConcurrentOperation(Function<byte[], Mono<Void>> save) {
        switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0:
                return save.apply(SHORT_BYTEARRAY);
            case 1:
                return save.apply(ELEVEN_KILOBYTES);
            case 2:
                return save.apply(TWELVE_MEGABYTES);
            default:
                return checkConcurrentSaveOperation();
        }
    }

    default Mono<Void> checkConcurrentSaveOperation() {
        return Mono
            .fromCallable(() ->
                testee().read(TEST_BUCKET_NAME, TEST_BLOB_ID))
            .flatMap(inputstream -> Mono.fromCallable(() -> IOUtils.toByteArray(inputstream)))
            .doOnNext(inputStream -> assertThat(inputStream).isIn(
                SHORT_BYTEARRAY,
                ELEVEN_KILOBYTES,
                TWELVE_MEGABYTES
            ))
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
                int value = read();
                if (value != -1) {
                    b[off] = (byte) value;
                }
                return value;
            }

        };
    }

}
