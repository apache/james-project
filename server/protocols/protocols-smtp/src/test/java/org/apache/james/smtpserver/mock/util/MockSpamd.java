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
package org.apache.james.smtpserver.mock.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.commons.io.IOUtils;

/**
 * This class can be used to run a mocked SPAMD daemon
 */
public class MockSpamd implements Runnable {

    /**
     * Mailcontent which is 100% spam
     */
    public final static String GTUBE = "-SPAM-";
    public final static String NOT_SPAM = "Spam: False ; 3 / 5";
    public final static String SPAM = "Spam: True ; 1000 / 5";
    private BufferedReader in;
    private OutputStream out;
    private Socket spamd;
    private ServerSocket socket;


    /**
     * Init the mocked SPAMD daemon
     *
     * @throws IOException
     */
    public MockSpamd(int port) throws IOException {
        socket = new ServerSocket(port);
    }
    
    /**
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
        try {
            boolean spam = false;

            // Accept connections
            spamd = socket.accept();

            in = new BufferedReader(new InputStreamReader(spamd.getInputStream()));
            out = spamd.getOutputStream();

            String line;

            // Parse the message
            while ((line = in.readLine()) != null) {
                if (line.contains(GTUBE)) {
                    spam = true;
                }
            }
            if (spam) {
                out.write(SPAM.getBytes());
                out.flush();
            } else {
                out.write(NOT_SPAM.getBytes());
                out.flush();
            }
        } catch (IOException e) {
            // Should not happen
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
            IOUtils.closeQuietly(spamd);
            IOUtils.closeQuietly(socket);
        }
    }
}
