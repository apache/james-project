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

package org.apache.james.blob.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FixedLengthInputStreamTest {

    @Test
    void fixedLengthInputStreamShouldThrowWhenInputStreamIsNull() {
        assertThatThrownBy(() -> new Store.FixedLengthInputStream(null, 0))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("'inputStream' is mandatory");
    }

    @Test
    void fixedLengthInputStreamShouldThrowWhenContentLengthIsNegative() {
        assertThatThrownBy(() -> new Store.FixedLengthInputStream(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)), -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'contentLength' should be greater than or equal to 0");
    }

    @Test
    void lengthShouldBeStored() {
        int contentLength = 1;

        Store.FixedLengthInputStream testee = new Store.FixedLengthInputStream(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)), contentLength);

        assertThat(testee.getContentLength()).isEqualTo(contentLength);
    }

    @Test
    void inputStreamShouldBeStored() {
        ByteArrayInputStream inputStream = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));

        Store.FixedLengthInputStream testee = new Store.FixedLengthInputStream(inputStream, 1);

        assertThat(testee.getInputStream()).hasSameContentAs(inputStream);
    }
}