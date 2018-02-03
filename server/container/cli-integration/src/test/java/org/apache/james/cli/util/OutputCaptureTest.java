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

package org.apache.james.cli.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class OutputCaptureTest {

    @Test
    public void contentShouldBeEmptyByDefault() {
        assertThat(new OutputCapture().getContent()).isEmpty();
    }

    @Test
    public void contentShouldReturnOutputStreamInput() throws Exception {
        OutputCapture outputCapture = new OutputCapture();

        String message = "Hello world!\n";
        outputCapture.getPrintStream().write(message.getBytes(StandardCharsets.UTF_8));

        assertThat(outputCapture.getContent()).isEqualTo(message);
    }


    @Test
    public void mixingReadsAndWritesShouldWork() throws Exception {
        OutputCapture outputCapture = new OutputCapture();
        String message = "Hello world!\n";
        outputCapture.getPrintStream().write(message.getBytes(StandardCharsets.UTF_8));
        outputCapture.getContent();

        String additionalMessage = "Additional message!\n";
        outputCapture.getPrintStream().write(additionalMessage.getBytes(StandardCharsets.UTF_8));

        assertThat(outputCapture.getContent()).isEqualTo(message + additionalMessage);
    }
}
