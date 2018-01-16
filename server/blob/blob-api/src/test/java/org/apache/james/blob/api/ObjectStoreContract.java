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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;

public interface ObjectStoreContract {

    ObjectStore testee();
    BlobId from(String blodIdAsString);

    @Test
    default void saveShouldReturnEmptyWhenNullData() throws Exception {
        assertThatThrownBy(() -> testee().save(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveShouldSaveEmptyData() throws Exception {
        BlobId blobId = testee().save(new byte[]{}).join();

        byte[] bytes = testee().read(blobId).join();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    default void saveShouldReturnBlobId() throws Exception {
        BlobId blobId = testee().save("toto".getBytes(StandardCharsets.UTF_8)).join();

        assertThat(blobId).isEqualTo(from("31f7a65e315586ac198bd798b6629ce4903d0899476d5741a9f32e2e521b6a66"));
    }

    @Test
    default void readShouldBeEmptyWhenNoExisting() throws IOException {
        byte[] bytes = testee().read(from("unknown")).join();

        assertThat(bytes).isEmpty();
    }

    @Test
    default void readShouldReturnSavedData() throws IOException {
        BlobId blobId = testee().save("toto".getBytes(StandardCharsets.UTF_8)).join();

        byte[] bytes = testee().read(blobId).join();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("toto");
    }

    @Test
    default void readShouldReturnLongSavedData() throws IOException {
        String longString = Strings.repeat("0123456789\n", 1000);
        BlobId blobId = testee().save(longString.getBytes(StandardCharsets.UTF_8)).join();

        byte[] bytes = testee().read(blobId).join();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(longString);
    }
}
