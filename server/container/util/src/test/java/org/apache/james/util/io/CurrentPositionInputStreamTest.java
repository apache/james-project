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

class CurrentPositionInputStreamTest {
    static final byte[] BYTES = "0123456789".getBytes(StandardCharsets.UTF_8);
    static final byte[] TWELVE_MEGABYTES = Strings.repeat("0123456789\r\n", 1024 * 1024).getBytes(StandardCharsets.UTF_8);

    @Test
    void positionInputStreamShouldNotAlterContent() {
        CurrentPositionInputStream currentPositionInputStream = new CurrentPositionInputStream(new ByteArrayInputStream(BYTES));

        assertThat(currentPositionInputStream).hasSameContentAs(new ByteArrayInputStream(BYTES));
    }

    @Test
    void positionInputStreamShouldNotAlterContentOfEmptyStream() {
        CurrentPositionInputStream currentPositionInputStream = new CurrentPositionInputStream(new ByteArrayInputStream(new byte[0]));

        assertThat(currentPositionInputStream).hasSameContentAs(new ByteArrayInputStream(new byte[0]));
    }

    @Test
    void positionInputStreamShouldNotAlterContentOfBigStream() {
        CurrentPositionInputStream currentPositionInputStream = new CurrentPositionInputStream(new ByteArrayInputStream(TWELVE_MEGABYTES));

        assertThat(currentPositionInputStream).hasSameContentAs(new ByteArrayInputStream(TWELVE_MEGABYTES));
    }

    @Test
    void getPositionShouldReturnZeroWhenEmpty() {
        CurrentPositionInputStream currentPositionInputStream = new CurrentPositionInputStream(new ByteArrayInputStream(new byte[0]));

        assertThat(currentPositionInputStream.getPosition()).isEqualTo(0);
    }

    @Test
    void getPositionShouldReturnPositionWhenReadWithABiggerBuffer() throws Exception {
        CurrentPositionInputStream currentPositionInputStream = new CurrentPositionInputStream(new ByteArrayInputStream(BYTES));

        currentPositionInputStream.read(new byte[24]);

        assertThat(currentPositionInputStream.getPosition()).isEqualTo(10);
    }

    @Test
    void getPositionShouldReturnPositionWhenReadWithABufferHavingSamePositionThanContent() throws Exception {
        CurrentPositionInputStream currentPositionInputStream = new CurrentPositionInputStream(new ByteArrayInputStream(BYTES));

        currentPositionInputStream.read(new byte[10]);

        assertThat(currentPositionInputStream.getPosition()).isEqualTo(10);
    }

    @Test
    void getPositionShouldReturnPositionWhenReadUsingSmallerBuffers() throws Exception {
        CurrentPositionInputStream currentPositionInputStream = new CurrentPositionInputStream(new ByteArrayInputStream(BYTES));

        currentPositionInputStream.read(new byte[6]);
        currentPositionInputStream.read(new byte[6]);

        assertThat(currentPositionInputStream.getPosition()).isEqualTo(10);
    }

    @Test
    void getPositionShouldReturnPositionWhenReadByte() {
        CurrentPositionInputStream currentPositionInputStream = new CurrentPositionInputStream(new ByteArrayInputStream(BYTES));

        IntStream.range(0, 10).forEach(Throwing.intConsumer(step -> currentPositionInputStream.read()));

        assertThat(currentPositionInputStream.getPosition()).isEqualTo(10);
    }

    @Test
    void getPositionShouldReturnPositionWhenSkips() throws Exception {
        CurrentPositionInputStream currentPositionInputStream = new CurrentPositionInputStream(new ByteArrayInputStream(BYTES));

        currentPositionInputStream.read(new byte[6]);
        currentPositionInputStream.skip(6);

        assertThat(currentPositionInputStream.getPosition()).isEqualTo(10);
    }

    @Test
    void getPositionShouldReturnPartialRead() throws Exception {
        CurrentPositionInputStream currentPositionInputStream = new CurrentPositionInputStream(new ByteArrayInputStream(BYTES));

        currentPositionInputStream.read(new byte[6]);

        assertThat(currentPositionInputStream.getPosition()).isEqualTo(6);
    }
}