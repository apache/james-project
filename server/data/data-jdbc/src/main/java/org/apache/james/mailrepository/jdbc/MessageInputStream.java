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

package org.apache.james.mailrepository.jdbc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.repository.api.StreamRepository;
import org.apache.james.server.core.MimeMessageUtil;
import org.apache.james.server.core.MimeMessageWrapper;
import org.apache.mailet.Mail;

/**
 * This class provides an inputStream for a Mail object.<br>
 * If the Mail is larger than 4KB it uses Piped streams and a worker thread,
 * otherwise it simply creates a temporary byte buffer and does not create the
 * worker thread.
 * 
 * <strong>Note</strong>: Javamail (or the Activation Framework) already uses a
 * worker thread when asked for an inputstream.
 */
final class MessageInputStream extends InputStream {

    /**
     * The size of the current message
     */
    private long size = -1;
    /**
     * The wrapped stream (Piped or Binary)
     */
    private InputStream wrapped;
    /**
     * If an excaption happens in the worker threads it's stored here
     */
    private Exception caughtException;
    /**
     * Stream repository used for dbfiles (null otherwise)
     */
    private final StreamRepository streamRep;

    /**
     * Main constructor. If srep is not null than we are using dbfiles and we
     * stream the body to file and only the header to db.
     * 
     * @param mc
     *            the Mail
     * @param srep
     *            the StreamRepository the StreamRepository used for dbfiles.
     * @param sizeLimit
     *            the sizeLimit which set the limit after which the streaming
     *            will be disabled
     * @throws IOException
     *             get thrown if an IO error detected
     * @throws MessagingException
     *             get thrown if an error detected while reading information of
     *             the mail
     */
    public MessageInputStream(Mail mc, StreamRepository srep, int sizeLimit, final boolean update) throws IOException, MessagingException {
        super();
        caughtException = null;
        streamRep = srep;
        size = mc.getMessageSize();

        // we use the pipes only when streamRep is null and the message size is
        // greater than 4096
        // Otherwise we should calculate the header size and not the message
        // size when streamRep is not null (JAMES-475)
        if (streamRep == null && size > sizeLimit) {
            PipedOutputStream headerOut = new PipedOutputStream();
            new Thread() {
                private Mail mail;

                private PipedOutputStream out;

                @Override
                public void run() {
                    try {
                        writeStream(mail, out, update);
                    } catch (IOException | MessagingException e) {
                        caughtException = e;
                    }
                }

                public Thread setParam(Mail mc, PipedOutputStream headerOut) {
                    this.mail = mc;
                    this.out = headerOut;
                    return this;
                }
            }.setParam(mc, (PipedOutputStream) headerOut).start();
            wrapped = new PipedInputStream(headerOut);
        } else {
            ByteArrayOutputStream headerOut = new ByteArrayOutputStream();
            writeStream(mc, headerOut, update);
            wrapped = new ByteArrayInputStream(headerOut.toByteArray());
            size = headerOut.size();
        }
    }

    /**
     * Returns the size of the full message
     * 
     * @return size the full message size
     */
    public long getSize() {
        return size;
    }

    /**
     * Write the full mail to the stream This can be used by this object or by
     * the worker threads.
     * 
     * @param mail
     *            the Mail used as source
     * @param out
     *            the OutputStream writting the mail to
     * @throws IOException
     *             get thrown if an IO error detected
     * @throws MessagingException
     *             get thrown if an error detected while reading information of
     *             the mail
     */
    private void writeStream(Mail mail, OutputStream out, boolean update) throws IOException, MessagingException {
        MimeMessage msg = mail.getMessage();

        if (update && msg instanceof MimeMessageWrapper) {
            MimeMessageWrapper wrapper = (MimeMessageWrapper) msg;
            wrapper.loadMessage();
        }

        OutputStream bodyOut = null;
        try {
            if (streamRep == null) {
                // If there is no filestore, use the byte array to store headers
                // and the body
                bodyOut = out;
            } else {
                // Store the body in the stream repository
                bodyOut = streamRep.put(mail.getName());
            }

            if (msg instanceof MimeMessageWrapper) {
                ((MimeMessageWrapper) msg).writeTo(out, bodyOut, null, true);
            } else {
                // Write the message to the headerOut and bodyOut. bodyOut goes
                // straight to the file
                MimeMessageUtil.writeTo(mail.getMessage(), out, bodyOut);
            }

            out.flush();
            bodyOut.flush();

        } finally {
            closeOutputStreams(out, bodyOut);
        }
    }

    private void throwException() throws IOException {
        try {
            if (wrapped == null) {
                throw new IOException("wrapped stream does not exists anymore");
            } else if (caughtException instanceof IOException) {
                throw (IOException) caughtException;
            } else {
                throw new IOException("Exception caugth in worker thread " + caughtException.getMessage()) {
                    @Override
                    public Throwable getCause() {
                        return caughtException;
                    }
                };
            }
        } finally {
            caughtException = null;
            wrapped = null;
        }
    }

    /**
     * Closes output streams used to update message
     * 
     * @param headerStream
     *            the stream containing header information - potentially the
     *            same as the body stream
     * @param bodyStream
     *            the stream containing body information
     * @throws IOException
     */
    private void closeOutputStreams(OutputStream headerStream, OutputStream bodyStream) throws IOException {
        try {
            // If the header stream is not the same as the body stream,
            // close the header stream here.
            if ((headerStream != null) && (headerStream != bodyStream)) {
                headerStream.close();
            }
        } finally {
            if (bodyStream != null) {
                bodyStream.close();
            }
        }
    }

    // wrapper methods

    @Override
    public int available() throws IOException {
        if (caughtException != null || wrapped == null) {
            throwException();
        }
        return wrapped.available();
    }

    @Override
    public void close() throws IOException {
        if (caughtException != null || wrapped == null) {
            throwException();
        }
        wrapped.close();
        wrapped = null;
    }

    @Override
    public synchronized void mark(int readLimit) {
        wrapped.mark(readLimit);
    }

    @Override
    public boolean markSupported() {
        return wrapped.markSupported();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (caughtException != null || wrapped == null) {
            throwException();
        }
        return wrapped.read(b, off, len);
    }

    @Override
    public int read(byte[] b) throws IOException {
        if (caughtException != null || wrapped == null) {
            throwException();
        }
        return wrapped.read(b);
    }

    @Override
    public synchronized void reset() throws IOException {
        if (caughtException != null || wrapped == null) {
            throwException();
        }
        wrapped.reset();
    }

    @Override
    public long skip(long n) throws IOException {
        if (caughtException != null || wrapped == null) {
            throwException();
        }
        return wrapped.skip(n);
    }

    @Override
    public int read() throws IOException {
        if (caughtException != null || wrapped == null) {
            throwException();
        }
        return wrapped.read();
    }

}
