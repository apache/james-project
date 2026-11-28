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

package org.apache.james.imapserver.netty;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openjdk.jcstress.Main;

class ReactiveThrottlerJCStressTest {
    @AfterEach
    void tearDown() throws IOException {
        // jcstress writes its binary result file in the working directory
        try (DirectoryStream<Path> resultFiles = Files.newDirectoryStream(Path.of("."), "jcstress-results-*.bin.gz")) {
            for (Path resultFile : resultFiles) {
                Files.deleteIfExists(resultFile);
            }
        }
    }

    @Test
    void requestsShouldNotBeLeftWaitingInTheQueue() {
        // jcstress throws an AssertionError when a FORBIDDEN outcome is observed or when the run fails
        assertThatCode(() -> Main.main(new String[] {
            "-t", ReactiveThrottlerLostWakeUpStress.class.getName(),
            "-m", "quick",
            "-c", "2",
            "-sc", "false",
            "-r", "target/jcstress-results"}))
            .doesNotThrowAnyException();
    }
}
