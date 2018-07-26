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
package org.apache.james.spamassassin.mock;

import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * This class can be used to run a mocked SPAMD daemon
 */
public class MockSpamd implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MockSpamd.class);

    /**
     * Mailcontent which is 100% spam
     */
    public static final String GTUBE = "-SPAM-";
    public static final String NOT_SPAM = "Spam: False ; 3 / 5";
    public static final String SPAM = "Spam: True ; 1000 / 5";

    private ServerSocket socket;
    private boolean isBinded;

    public MockSpamd() {
        isBinded = false;
    }

    public int getPort() {
        Preconditions.checkState(isBinded, "SpamD mock server is not binded");
        return socket.getLocalPort();
    }

    public void bind() throws IOException {
        socket = new ServerSocket(0);
        isBinded = true;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = socket;
            Socket spamd = serverSocket.accept();
             BufferedReader in = new BufferedReader(new InputStreamReader(spamd.getInputStream()));
             OutputStream out = spamd.getOutputStream()) {

            handleRequest(in, out);
        } catch (IOException e) {
            LOGGER.error("Exception while handling answer", e);
        }
    }

    private void handleRequest(BufferedReader in, OutputStream out) throws IOException {
        if (isSpam(in)) {
            out.write(SPAM.getBytes());
        } else {
            out.write(NOT_SPAM.getBytes());
        }
        out.flush();
    }

    private boolean isSpam(BufferedReader in) throws IOException {
        try {
            return in.lines()
                .anyMatch(line -> line.contains(GTUBE));
        } finally {
            consume(in);
        }
    }

    private void consume(BufferedReader in) throws IOException {
        IOUtils.copy(in, NULL_OUTPUT_STREAM, StandardCharsets.UTF_8);
    }
}
