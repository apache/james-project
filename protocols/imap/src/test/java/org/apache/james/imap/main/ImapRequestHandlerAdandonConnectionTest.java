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
package org.apache.james.imap.main;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.decode.main.ImapRequestStreamHandler;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.encode.ImapEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImapRequestHandlerAdandonConnectionTest {

    /** System under test */
    ImapRequestStreamHandler subject;
    
    // Fakes
    /** Stores output */
    ByteArrayOutputStream fakeOutput;
    
    // Stubs
    ImapDecoder decoderStub;
    ImapProcessor processorStub;
    ImapEncoder encoderStub;
    
    @BeforeEach
    void setUp() throws Exception {
        // Fakes
        fakeOutput = new ByteArrayOutputStream();
        // Stubs
        decoderStub = mock(ImapDecoder.class);
        processorStub = mock(ImapProcessor.class);
        encoderStub = mock(ImapEncoder.class);
        // System under test
        subject = new ImapRequestStreamHandler(decoderStub, processorStub, encoderStub);
    }
    
    @Test
    void testWhenConsumeLineFailsShouldAbandonConnection() {
        // Create input stream that will throw IOException after first read
        byte[] endOfStreamAfterOneCharacter = {'0'};
        ByteArrayInputStream fakeInput = new ByteArrayInputStream(endOfStreamAfterOneCharacter);
        
        // 
        // Exercise
        //
        boolean result = subject.handleRequest(fakeInput, fakeOutput, new FakeImapSession());
        
        //
        // Verify output
        //
        assertThat(result).describedAs("Connection should be abandoned").isFalse();
    }
}
