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

package org.apache.james.server.core;

import java.io.IOException;
import java.io.InputStream;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

/**
 * Provide an {@link InputStream} over an {@link MimeMessage}
 */
public class MimeMessageInputStream extends InputStream {
    private final InputStream in;

    /**
     * Provide an {@link InputStream} over a {@link MimeMessage}.
     * 
     * @param message
     *            the message to wrap
     * @param tryCast
     *            try to cast the {@link MimeMessage} to
     *            {@link MimeMessageWrapper} to do some optimized processing if
     *            possible
     * @throws MessagingException
     */
    public MimeMessageInputStream(MimeMessage message, boolean tryCast) throws MessagingException {
        // check if we can use optimized operations
        if (tryCast && message instanceof MimeMessageWrapper) {
            in = ((MimeMessageWrapper) message).getMessageInputStream();
        } else {
            UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream();
            try {
                message.writeTo(out);
                in = out.toInputStream();
            } catch (IOException e1) {
                throw new MessagingException("Unable to read message " + message, e1);
            }
        }
    }

    /**
     * Use true as tryCast parameter
     * 
     * {@link #MimeMessageInputStream(MimeMessage, boolean)}
     */
    public MimeMessageInputStream(MimeMessage message) throws MessagingException {
        this(message, true);
    }

    @Override
    public int read() throws IOException {
        return in.read();
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    @Override
    public int available() throws IOException {
        return in.available();
    }

    @Override
    public void mark(int readlimit) {
        in.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        return in.markSupported();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return in.read(b, off, len);
    }

    @Override
    public int read(byte[] b) throws IOException {
        return in.read(b);
    }

    @Override
    public void reset() throws IOException {
        in.reset();
    }

    @Override
    public long skip(long n) throws IOException {
        return in.skip(n);
    }

}
