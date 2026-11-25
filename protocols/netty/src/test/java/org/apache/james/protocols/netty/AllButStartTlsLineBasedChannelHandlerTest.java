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
package org.apache.james.protocols.netty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledHeapByteBuf;

class AllButStartTlsLineBasedChannelHandlerTest {
    @Test
    void aLineReceivedByteByByteShouldBeSearchedOnce() {
        AllButStartTlsLineBasedChannelHandler handler = new AllButStartTlsLineBasedChannelHandler(null, 65536, false, "starttls");
        // Not recording the invocations: a regression would search the buffer a lot
        ByteBuf buffer = mock(UnpooledHeapByteBuf.class, withSettings()
            .spiedInstance(new UnpooledHeapByteBuf(UnpooledByteBufAllocator.DEFAULT, 16, 65536))
            .defaultAnswer(CALLS_REAL_METHODS)
            .stubOnly());
        // bytesBefore searches through indexOf too
        AtomicLong searchedBytes = new AtomicLong();
        doAnswer(invocation -> {
            searchedBytes.addAndGet((int) invocation.getArgument(1) - (int) invocation.getArgument(0));
            return invocation.callRealMethod();
        }).when(buffer).indexOf(anyInt(), anyInt(), anyByte());
        int length = 4_000;

        for (int i = 0; i < length; i++) {
            buffer.writeByte('x');
            assertThat(handler.bytesBeforeLineEnd(buffer)).isEqualTo(-1);
        }
        buffer.writeByte('\n');

        assertThat(handler.bytesBeforeLineEnd(buffer)).isEqualTo(length);
        assertThat(searchedBytes.get()).isLessThanOrEqualTo(length + 1);
    }
}
