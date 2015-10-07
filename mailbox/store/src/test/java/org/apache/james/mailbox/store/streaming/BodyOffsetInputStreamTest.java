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
package org.apache.james.mailbox.store.streaming;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;

public class BodyOffsetInputStreamTest {
    private String mail = "Subject: test\r\n\r\nbody";
    private long expectedOffset = 17;
    private long bytes = mail.length();
    
    @Test
    public void testRead() throws IOException {
        BodyOffsetInputStream in = new BodyOffsetInputStream(new ByteArrayInputStream(mail.getBytes()));
        
        while (in.read() != -1) {
            // consume stream
        }
        assertEquals(expectedOffset, in.getBodyStartOffset());
        assertEquals(bytes, in.getReadBytes());
        in.close();
    }
    
    
    @Test
    public void testReadWithArray() throws IOException {
        BodyOffsetInputStream in = new BodyOffsetInputStream(new ByteArrayInputStream(mail.getBytes()));
        
        byte[] b = new byte[8];
        while (in.read(b) != -1) {
            // consume stream
        }
        assertEquals(expectedOffset, in.getBodyStartOffset());
        assertEquals(bytes, in.getReadBytes());
        in.close();
    }
    
    
    @Test
    public void testReadWithArrayBiggerThenStream() throws IOException {
        BodyOffsetInputStream in = new BodyOffsetInputStream(new ByteArrayInputStream(mail.getBytes()));
        
        byte[] b = new byte[4096];
        while (in.read(b) != -1) {
            // consume stream
        }
        assertEquals(expectedOffset, in.getBodyStartOffset());
        assertEquals(bytes, in.getReadBytes());
        in.close();
    }
}
