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

package org.apache.james.blob.objectstorage.aws.sse;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.Duration;
import java.util.Map;

import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

public class S3SSECustomerKeyGeneratorTest {
    record SampleTest(String masterPassword, String salt, String expectedCustomerKey) {
    }

    static Map<Integer, SampleTest> SAMPLE_TEST = ImmutableMap.of(
        1, new SampleTest("test1", "salt1", "Le39M/h9Wi72qMMV9O4ibqZxWb9cL3rQRUy0rfMdODc="),
        2, new SampleTest("test2", "salt2", "Yo8T24EXwAZm3lyGyzfiyYx0FcSdK5ai9pmgO6yLDe8="),
        3, new SampleTest("test3", "salt3", "KGMvLVMB0Y7U2WpYiQmbWn+g9fE3ZvH0pSrx1BzNups="),
        4, new SampleTest("test4", "salt4", "kGbGrfAgfAjkEByylQoFO77yR0JeSA44fhnkuREdrkY="),
        5, new SampleTest("test5", "salt5", "dxaGCxA7c7bmG2gEjHkfLMvH6SFUQp0d0wa0xKuia3Q="),
        6, new SampleTest("test6", "salt6", "GhNsbP2E/2uSMpgD59jR1W8oTmoRwZ/bo1ScSqh4wiQ="),
        7, new SampleTest("test7", "salt7", "X2VE+qpQnTFnWN5e/SgQyoyBXfxVM3Oy2+2f8Auba6o="),
        8, new SampleTest("test8", "salt8", "EVz2OnBEOyfuH4h4p5X7utZVJ+LXAojnti102Wzp6Lc="),
        9, new SampleTest("test9", "salt9", "HNA8IazLNDFGATKjBjzeNJPZ6fn9/g19rzIFAHYVbGs="),
        10, new SampleTest("test10", "salt10", "8R4WuFhTDpRMsQjht6XMrq4CFODC9oRZ/zucRXZ9+JU="));

    @Test
    void generateShouldWorkCorrectWhenConcurrent() throws Exception {
        S3SSECustomerKeyGenerator testee = new S3SSECustomerKeyGenerator("PBKDF2WithHmacSHA256");
        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> {
                int index = threadNumber + 1;
                String customerKey = testee.generateCustomerKey("test" + index, "salt" + index);
                assertThat(customerKey).isEqualTo(SAMPLE_TEST.get(index).expectedCustomerKey());
            }))
            .threadCount(10)
            .operationCount(100)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }
}
