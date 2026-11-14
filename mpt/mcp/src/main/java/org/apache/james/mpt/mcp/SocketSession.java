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

package org.apache.james.mpt.mcp;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.james.mpt.api.Session;

/**
 * Blocking socket session, optionally using implicit TLS, supporting STARTTLS upgrades.
 */
public class SocketSession implements Session {
    private static final int MAX_LINE_BYTES = 1024 * 1024;

    private final int id;
    private final Target target;
    private final SSLSocketFactory sslSocketFactory;
    private final boolean verifyHostname;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Transcript transcript;

    private Socket socket;
    private InputStream in;
    private OutputStream out;

    public SocketSession(int id, Target target, SSLSocketFactory sslSocketFactory, boolean verifyHostname,
                         Duration connectTimeout, Duration readTimeout, Transcript transcript) {
        this.id = id;
        this.target = target;
        this.sslSocketFactory = sslSocketFactory;
        this.verifyHostname = verifyHostname;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.transcript = transcript;
    }

    @Override
    public void start() throws Exception {
        transcript.record(id, Transcript.Direction.EVENT, "connecting to " + target.name() + " (" + target.uri() + ")");
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(target.host(), target.port()), (int) connectTimeout.toMillis());
        plain.setSoTimeout((int) readTimeout.toMillis());
        socket = plain;
        if (target.tls()) {
            socket = wrap(plain);
        }
        bindStreams();
    }

    @Override
    public void startTls() throws Exception {
        if (socket instanceof SSLSocket) {
            throw new IOException("Session " + id + " is already using TLS");
        }
        socket = wrap(socket);
        bindStreams();
    }

    private SSLSocket wrap(Socket plain) throws IOException {
        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(plain, target.host(), target.port(), true);
        if (verifyHostname) {
            SSLParameters parameters = sslSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(parameters);
        }
        sslSocket.setSoTimeout((int) readTimeout.toMillis());
        sslSocket.startHandshake();
        transcript.record(id, Transcript.Direction.EVENT, "TLS established: " + sslSocket.getSession().getProtocol()
            + " " + sslSocket.getSession().getCipherSuite());
        return sslSocket;
    }

    private void bindStreams() throws IOException {
        in = new BufferedInputStream(socket.getInputStream());
        out = socket.getOutputStream();
    }

    @Override
    public String readLine() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (true) {
            int next = in.read();
            if (next == -1) {
                if (buffer.size() > 0) {
                    String partial = buffer.toString(UTF_8);
                    transcript.record(id, Transcript.Direction.SERVER, partial);
                    transcript.record(id, Transcript.Direction.EVENT, "WARNING: last line not terminated by CRLF, connection closed by server");
                    return partial;
                }
                transcript.record(id, Transcript.Direction.EVENT, "connection closed by server");
                throw new EOFException("connection closed by server");
            }
            if (next == '\n') {
                break;
            }
            buffer.write(next);
            if (buffer.size() > MAX_LINE_BYTES) {
                throw new IOException("Server line exceeds " + MAX_LINE_BYTES + " bytes");
            }
        }
        byte[] bytes = buffer.toByteArray();
        int length = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
        String line = new String(bytes, 0, length, UTF_8);
        transcript.record(id, Transcript.Direction.SERVER, line);
        return line;
    }

    @Override
    public void writeLine(String line) throws Exception {
        transcript.record(id, Transcript.Direction.CLIENT, line);
        out.write((line + "\r\n").getBytes(UTF_8));
        out.flush();
    }

    @Override
    public void restart() throws Exception {
        stop();
        start();
    }

    @Override
    public void stop() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    @Override
    public void await() {
        // Nothing to wait for: the server runs out of process. Use WAIT or UNTIL instead.
    }
}
