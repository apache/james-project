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

package org.apache.james.util.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Strings;

class SizeInputStreamTest {
    static final byte[] BYTES = "0123456789".getBytes(StandardCharsets.UTF_8);
    static final byte[] TWELVE_MEGABYTES = Strings.repeat("0123456789\r\n", 1024 * 1024).getBytes(StandardCharsets.UTF_8);

    @Test
    void sizeInputStreamShouldNotAlterContent() {
        SizeInputStream sizeInputStream = new SizeInputStream(new ByteArrayInputStream(BYTES));

        assertThat(sizeInputStream).hasSameContentAs(new ByteArrayInputStream(BYTES));
    }

    @Test
    void sizeInputStreamShouldNotAlterContentOfEmptyStream() {
        SizeInputStream sizeInputStream = new SizeInputStream(new ByteArrayInputStream(new byte[0]));

        assertThat(sizeInputStream).hasSameContentAs(new ByteArrayInputStream(new byte[0]));
    }

    @Test
    void sizeInputStreamShouldNotAlterContentOfBigStream() {
        SizeInputStream sizeInputStream = new SizeInputStream(new ByteArrayInputStream(TWELVE_MEGABYTES));

        assertThat(sizeInputStream).hasSameContentAs(new ByteArrayInputStream(TWELVE_MEGABYTES));
    }

    @Test
    void getSizeShouldReturnZeroWhenEmpty() {
        SizeInputStream sizeInputStream = new SizeInputStream(new ByteArrayInputStream(new byte[0]));

        assertThat(sizeInputStream.getSize()).isEqualTo(0);
    }

    @Test
    void getSizeShouldReturnSizeWhenReadWithABiggerBuffer() throws Exception {
        SizeInputStream sizeInputStream = new SizeInputStream(new ByteArrayInputStream(BYTES));

        sizeInputStream.read(new byte[24]);

        assertThat(sizeInputStream.getSize()).isEqualTo(10);
    }

    @Test
    void getSizeShouldReturnSizeWhenReadWithABufferHavingSameSizeThanContent() throws Exception {
        SizeInputStream sizeInputStream = new SizeInputStream(new ByteArrayInputStream(BYTES));

        sizeInputStream.read(new byte[10]);

        assertThat(sizeInputStream.getSize()).isEqualTo(10);
    }

    @Test
    void getSizeShouldReturnSizeWhenReadUsingSmallerBuffers() throws Exception {
        SizeInputStream sizeInputStream = new SizeInputStream(new ByteArrayInputStream(BYTES));

        sizeInputStream.read(new byte[6]);
        sizeInputStream.read(new byte[6]);

        assertThat(sizeInputStream.getSize()).isEqualTo(10);
    }

    @Test
    void getSizeShouldReturnSizeWhenReadByte() {
        SizeInputStream sizeInputStream = new SizeInputStream(new ByteArrayInputStream(BYTES));

        IntStream.range(0, 10).forEach(Throwing.intConsumer(step -> sizeInputStream.read()));

        assertThat(sizeInputStream.getSize()).isEqualTo(10);
    }

    @Test
    void getSizeShouldReturnSizeWhenSkips() throws Exception {
        SizeInputStream sizeInputStream = new SizeInputStream(new ByteArrayInputStream(BYTES));

        sizeInputStream.read(new byte[6]);
        sizeInputStream.skip(6);

        assertThat(sizeInputStream.getSize()).isEqualTo(10);
    }
}