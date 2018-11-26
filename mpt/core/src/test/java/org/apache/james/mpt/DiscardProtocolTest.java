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

package org.apache.james.mpt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;

import javax.net.SocketFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscardProtocolTest {

    private final class InputLater implements Runnable {
        private Exception e;
        
        @Override
        public void run() {
            try  {
                Thread.sleep(1000);
                input();
            } catch (Exception e) {
                this.e = e;
            }
        }
        
        void assertExecutedSuccessfully() throws Exception {
            if (e != null) {
                e.printStackTrace();
                throw e;
            }
        }
    }

    private static final String INPUT = "One, two, three - Testing";

    private DiscardProtocol protocol;
    private Socket socket;

    private DiscardProtocol.Record record;
    
    @BeforeEach
    void setUp() throws Exception {
        protocol = new DiscardProtocol();
        protocol.start();
        socket = SocketFactory.getDefault().createSocket("127.0.0.1", protocol.getPort().getValue());
        record = protocol.recordNext();
    }

    @AfterEach
    void tearDown() {
        protocol.stop();
    }

    @Test
    void testRecord() throws Exception {
        assertThat(socket.isConnected()).isTrue();
        input();
        String output = record.complete();
        assertThat(output).isEqualTo(INPUT);
    }

    private void input() throws IOException {
        Writer out = new OutputStreamWriter(socket.getOutputStream());
        out.append(INPUT);
        out.close();
        socket.close();
    }

    @Test
    void testComplete() throws Exception {
        InputLater inputLater = new InputLater();
        Thread thread = new Thread(inputLater);
        thread.start();
        String output = record.complete();
        assertThat(output).isEqualTo(INPUT);
        inputLater.assertExecutedSuccessfully();
    }
}
