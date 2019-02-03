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

import org.junit.Test;

public class CRLFTerminatedInputStreamTest extends AbstractInputStreamTest {

    @Test
    public void testCRLFPresent() throws IOException {
        String data = "Subject: test\r\n\r\ndata\r\n";
        checkRead(new CRLFTerminatedInputStream(new ByteArrayInputStream(data.getBytes())), data);
        checkReadViaArray(new CRLFTerminatedInputStream(new ByteArrayInputStream(data.getBytes())), data);

    }

    @Test
    public void testCRPresent() throws IOException {
        String data = "Subject: test\r\n\r\ndata\r";
        String expected = data + "\n";
        checkRead(new CRLFTerminatedInputStream(new ByteArrayInputStream(data.getBytes())), expected);
        checkReadViaArray(new CRLFTerminatedInputStream(new ByteArrayInputStream(data.getBytes())), expected);
    }

    @Test
    public void testNonPresent() throws IOException {
        String data = "Subject: test\r\n\r\ndata";
        String expected = data + "\r\n";
        checkRead(new CRLFTerminatedInputStream(new ByteArrayInputStream(data.getBytes())), expected);
        checkReadViaArray(new CRLFTerminatedInputStream(new ByteArrayInputStream(data.getBytes())), expected);

    }

}
