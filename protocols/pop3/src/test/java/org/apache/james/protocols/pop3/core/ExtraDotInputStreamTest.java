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

package org.apache.james.protocols.pop3.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

public class ExtraDotInputStreamTest extends AbstractInputStreamTest {
    @Test
    void testExtraDot() throws IOException {
        String data = "This\r\n.\r\nThis.\r\n";
        String expectedOutput = "This\r\n..\r\nThis.\r\n";
        
        checkRead(new ExtraDotInputStream(new ByteArrayInputStream(data.getBytes())), expectedOutput);
        checkReadViaArray(new ExtraDotInputStream(new ByteArrayInputStream(data.getBytes())), expectedOutput);

    }

    @Test
    void testExtraDotOnDoubleDot() throws IOException {
        String data = "This\r\n..\r\nThis.\r\n";
        String expectedOutput = "This\r\n...\r\nThis.\r\n";

        checkRead(new ExtraDotInputStream(new ByteArrayInputStream(data.getBytes())), expectedOutput);
        checkReadViaArray(new ExtraDotInputStream(new ByteArrayInputStream(data.getBytes())), expectedOutput);

    }

    @Test
    void testExtraDotOnDotWithText() throws IOException {
        String data = "This\r\n.TestText\r\nThis.\r\n";
        String expected = "This\r\n..TestText\r\nThis.\r\n";

        checkRead(new ExtraDotInputStream(new ByteArrayInputStream(data.getBytes())), expected);
        checkReadViaArray(new ExtraDotInputStream(new ByteArrayInputStream(data.getBytes())), expected);

    }

    @Test
    void testNoDotCLRF() throws IOException {
        String data = "ABCD\r\n";
        checkRead(new ExtraDotInputStream(new ByteArrayInputStream(data.getBytes())), data);
        checkReadViaArray(new ExtraDotInputStream(new ByteArrayInputStream(data.getBytes())), data);
    }

    @Test
    void testNoDot() throws IOException {
        String data = "ABCD";
        checkRead(new ExtraDotInputStream(new ByteArrayInputStream(data.getBytes())), data);
        checkReadViaArray(new ExtraDotInputStream(new ByteArrayInputStream(data.getBytes())), data);
    }

    // Proof of BUG JAMES-1152
    @Test
    void testNoDotHeaderBody() throws IOException {
        String data = "Subject: test\r\n\r\nABCD\r\n";
        checkRead(new ExtraDotInputStream(new ByteArrayInputStream(data.getBytes())), data);
        checkReadViaArray(new ExtraDotInputStream(new ByteArrayInputStream(data.getBytes())), data);
    }
}
