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

package org.apache.james.mpt.session;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.james.mpt.api.Monitor;
import org.apache.james.mpt.api.Session;
import org.awaitility.Awaitility;
import org.awaitility.Duration;

public final class ExternalSession implements Session {

    private static final byte[] CRLF = { '\r', '\n' };

    private final SocketChannel socket;

    private final Monitor monitor;

    private final ByteBuffer readBuffer;

    private final ByteBuffer lineEndBuffer;

    private boolean first = true;

    private final String shabang;

    public ExternalSession(SocketChannel socket, Monitor monitor, String shabang) {
        this(socket, monitor, shabang, false);
    }

    public ExternalSession(SocketChannel socket, Monitor monitor, String shabang, boolean debug) {
        super();
        this.socket = socket;
        this.monitor = monitor;
        readBuffer = ByteBuffer.allocateDirect(2048);
        lineEndBuffer = ByteBuffer.wrap(CRLF);
        this.shabang = shabang;
    }

    @Override
    public String readLine() throws Exception {
        StringBuffer buffer = new StringBuffer();
        readlineInto(buffer);
        final String result;
        if (first && shabang != null) {
            // fake shabang
            monitor.note("<-" + buffer.toString());
            result = shabang;
            first = false;
        } else {
            result = buffer.toString();
            monitor.note("<-" + result);
        }
        return result;
    }

    private void readlineInto(StringBuffer buffer) throws Exception {
        monitor.debug("[Reading line]");
        readBuffer.flip();
        while (oneFromLine(buffer)) {
            ;
        }
        // May have partial read
        readBuffer.compact();
        monitor.debug("[Done]");
    }

    private boolean oneFromLine(StringBuffer buffer) throws Exception {
        final boolean result;
        if (readBuffer.hasRemaining()) {
            char next = (char) readBuffer.get();
            if (next == '\n') {
                monitor.debug("[LF]");
                // Reached end of the line
                result = false;
            } else if (next == '\r') {
                // CRLF line endings so drop
                monitor.debug("[CR]");
                result = true;
            } else {
                // Load buffer
                monitor.debug(next);
                buffer.append(next);
                result = true;
            }
        } else {
            monitor.debug("[Reading into buffer]");
            readBuffer.clear();
            result = tryReadFromSocket();
            // Reset for transfer into string buffer
            readBuffer.flip();
            monitor.debug(String.format("[Read %d characters]", readBuffer.limit()));
        }
        return result;
    }

    private boolean tryReadFromSocket() throws IOException, InterruptedException {
        final MutableInt status = new MutableInt(0);
        Awaitility
            .waitAtMost(Duration.ONE_MINUTE)
            .pollDelay(new Duration(10, TimeUnit.MILLISECONDS))
            .until(() -> {
                int read = socket.read(readBuffer);
                status.setValue(read);
                return read != 0;
            });
        if (status.intValue() == -1) {
            monitor.debug("Error reading, got -1");
            return false;
        }
        return true;
    }

    @Override
    public void start() throws Exception {
        while (!socket.finishConnect()) {
            monitor.note("connecting...");
            Thread.sleep(10);
        }
    }

    @Override
    public void restart() throws Exception {
        throw new NotImplementedException("Restart is not implemented for ExternalSession");
    }

    @Override
    public void stop() throws Exception {
        monitor.note("closing");
        socket.close();
    }

    @Override
    public void writeLine(String line) throws Exception {
        monitor.note("-> " + line);
        monitor.debug("[Writing line]");
        ByteBuffer writeBuffer = US_ASCII.encode(line);
        while (writeBuffer.hasRemaining()) {
            socket.write(writeBuffer);
        }
        lineEndBuffer.rewind();
        while (lineEndBuffer.hasRemaining()) {
            socket.write(lineEndBuffer);
        }
        monitor.debug("[Done]");
    }

    @Override
    public void await() throws Exception {
        TimeUnit.SECONDS.sleep(5);
    }

    /**
     * Constructs a <code>String</code> with all attributes in name = value
     * format.
     * 
     * @return a <code>String</code> representation of this object.
     */
    public String toString() {
        final String TAB = " ";

        return "External ( " + "socket = " + this.socket + TAB + "monitor = " + this.monitor + TAB
                + "readBuffer = " + this.readBuffer + TAB + "lineEndBuffer = "
                + this.lineEndBuffer + TAB + "first = " + this.first + TAB + "shabang = " + this.shabang + TAB + " )";
    }

}
