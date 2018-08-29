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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;

public interface BlobStoreContract {

    BlobStore testee();

    BlobId.Factory blobIdFactory();

    @Test
    default void saveShouldReturnEmptyWhenNullData() throws Exception {
        assertThatThrownBy(() -> testee().save((byte[]) null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveShouldReturnEmptyWhenNullInputStream() throws Exception {
        assertThatThrownBy(() -> testee().save((InputStream) null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveShouldSaveEmptyData() throws Exception {
        BlobId blobId = testee().save(new byte[]{}).join();

        byte[] bytes = testee().readBytes(blobId).join();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    default void saveShouldSaveEmptyInputStream() throws Exception {
        BlobId blobId = testee().save(new ByteArrayInputStream(new byte[]{})).join();

        byte[] bytes = testee().readBytes(blobId).join();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    default void saveShouldReturnBlobId() throws Exception {
        BlobId blobId = testee().save("toto".getBytes(StandardCharsets.UTF_8)).join();

        assertThat(blobId).isEqualTo(blobIdFactory().from("31f7a65e315586ac198bd798b6629ce4903d0899476d5741a9f32e2e521b6a66"));
    }

    @Test
    default void saveShouldReturnBlobIdOfInputStream() throws Exception {
        BlobId blobId =
            testee().save(new ByteArrayInputStream("toto".getBytes(StandardCharsets.UTF_8))).join();

        assertThat(blobId).isEqualTo(blobIdFactory().from("31f7a65e315586ac198bd798b6629ce4903d0899476d5741a9f32e2e521b6a66"));
    }

    @Test
    default void readBytesShouldBeEmptyWhenNoExisting() throws IOException {
        byte[] bytes = testee().readBytes(blobIdFactory().from("unknown")).join();

        assertThat(bytes).isEmpty();
    }

    @Test
    default void readBytesShouldReturnSavedData() throws IOException {
        BlobId blobId = testee().save("toto".getBytes(StandardCharsets.UTF_8)).join();

        byte[] bytes = testee().readBytes(blobId).join();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("toto");
    }

    @Test
    default void readBytesShouldReturnLongSavedData() throws IOException {
        String longString = Strings.repeat("0123456789\n", 1000);
        BlobId blobId = testee().save(longString.getBytes(StandardCharsets.UTF_8)).join();

        byte[] bytes = testee().readBytes(blobId).join();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(longString);
    }

    @Test
    default void readBytesShouldReturnBigSavedData() throws IOException {
        // 12 MB of text
        String bigString = Strings.repeat("0123456789\r\n", 1024 * 1024);
        BlobId blobId = testee().save(bigString.getBytes(StandardCharsets.UTF_8)).join();

        byte[] bytes = testee().readBytes(blobId).join();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(bigString);
    }

    @Test
    default void readShouldBeEmptyWhenNoExistingStream() throws IOException {
        InputStream stream = testee().read(blobIdFactory().from("unknown"));

        assertThat(stream.read()).isEqualTo(IOUtils.EOF);
    }

    @Test
    default void readShouldReturnSavedData() throws IOException {
        byte[] bytes = "toto".getBytes(StandardCharsets.UTF_8);
        BlobId blobId = testee().save(bytes).join();

        InputStream read = testee().read(blobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(bytes));
    }

    @Test
    default void readShouldReturnLongSavedData() throws IOException {
        String longString = Strings.repeat("0123456789\n", 1000);
        byte[] bytes = longString.getBytes(StandardCharsets.UTF_8);
        BlobId blobId = testee().save(bytes).join();

        InputStream read = testee().read(blobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(bytes));
    }

    @Test
    default void readShouldReturnBigSavedData() throws IOException {
        // 12 MB of text
        String bigString = Strings.repeat("0123456789\r\n", 1024 * 1024);
        byte[] bytes = bigString.getBytes(StandardCharsets.UTF_8);
        BlobId blobId = testee().save(bytes).join();

        InputStream read = testee().read(blobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(bytes));
    }
}
