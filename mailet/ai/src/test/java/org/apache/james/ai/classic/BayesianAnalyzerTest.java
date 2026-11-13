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

package org.apache.james.ai.classic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class BayesianAnalyzerTest {
    @Test
    void concurrentTrainingShouldNotLoseMessageCounts() {
        BayesianAnalyzer testee = new BayesianAnalyzer();

        CompletableFuture.allOf(IntStream.range(0, 8)
            .mapToObj(thread -> CompletableFuture.runAsync(() -> {
                for (int i = 0; i < 500; i++) {
                    try {
                        testee.addHam(new StringReader(""));
                        testee.addSpam(new StringReader(""));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }))
            .toArray(CompletableFuture[]::new))
            .join();

        assertThat(testee.getHamMessageCount()).isEqualTo(4000);
        assertThat(testee.getSpamMessageCount()).isEqualTo(4000);
    }
}
