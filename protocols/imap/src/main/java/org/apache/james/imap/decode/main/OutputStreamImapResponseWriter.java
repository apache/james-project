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

package org.apache.james.imap.decode.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.imap.encode.ImapResponseWriter;
import org.apache.james.imap.message.Literal;

/**
 * Class providing methods to send response messages from the server to the
 * client.
 */
public class OutputStreamImapResponseWriter implements ImapResponseWriter {
    @FunctionalInterface
    interface FlushCallback {
        void run() throws IOException;
    }

    public static final int BUFFER_SIZE = 1024;
    private final OutputStream output;
    private FlushCallback flushCallback;

    public OutputStreamImapResponseWriter(OutputStream output) {
        this.output = output;
        this.flushCallback = () -> {

        };
    }

    public void setFlushCallback(FlushCallback flushCallback) {
        this.flushCallback = flushCallback;
    }

    @Override
    public void write(Literal literal) throws IOException {
        flushCallback.run();
        try (InputStream in = literal.getInputStream()) {
            IOUtils.copy(in, output, BUFFER_SIZE);
        }
    }

    @Override
    public void write(byte[] buffer) throws IOException {
        output.write(buffer);
    }

}
