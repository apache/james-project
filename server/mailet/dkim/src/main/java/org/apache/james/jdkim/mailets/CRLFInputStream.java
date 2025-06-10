/******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                 *
 * or more contributor license agreements.  See the NOTICE file               *
 * distributed with this work for additional information                      *
 * regarding copyright ownership.  The ASF licenses this file                 *
 * to you under the Apache License, Version 2.0 (the                          *
 * "License"); you may not use this file except in compliance                 *
 * with the License.  You may obtain a copy of the License at                 *
 *                                                                            *
 *   http://www.apache.org/licenses/LICENSE-2.0                               *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing,                 *
 * software distributed under the License is distributed on an                *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                     *
 * KIND, either express or implied.  See the License for the                  *
 * specific language governing permissions and limitations                    *
 * under the License.                                                         *
 ******************************************************************************/

package org.apache.james.jdkim.mailets;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;

/**
 * A Filter for use with SMTP or other protocols in which lines must end with
 * CRLF. Converts every "isolated" occurrence of \r or \n with \r\n
 * <p>
 * RFC 2821 #2.3.7 mandates that line termination is CRLF, and that CR and LF
 * must not be transmitted except in that pairing. If we get a naked LF, convert
 * to CRLF.
 */
public class CRLFInputStream extends FilterInputStream {
    private static final int LAST_WAS_OTHER = 0;
    private static final int LAST_WAS_CR = 1;
    private static final int LAST_WAS_LF = 2;

    /**
     * Aligned with {@link java.io.InputStream#DEFAULT_BUFFER_SIZE}
      */
    private static final int DEFAULT_BUFFER_SIZE = 16384;

    /**
     * Counter for number of last (0A or 0D).
     */
    private int statusLast;
    private boolean underlyingStreamHasNext = true;
    private final IntArrayFIFOQueue queue = new IntArrayFIFOQueue(DEFAULT_BUFFER_SIZE);

    public CRLFInputStream(InputStream in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        if (underlyingStreamHasNext) {
            int b = super.read();

            // not at the enf
            doEnqueue(b);
        }
        if (queue.isEmpty()) {
            return -1;
        } else {
            return queue.dequeueInt();
        }
    }

    private void doEnqueue(int b) {
        switch (b) {
            case '\r':
                queue.enqueue(b);
                queue.enqueue('\n');
                statusLast = LAST_WAS_CR;
                break;
            case '\n':
                if (statusLast != LAST_WAS_CR) {
                    queue.enqueue('\r');
                    queue.enqueue(b);
                }
                statusLast = LAST_WAS_LF;
                break;
            case -1:
                underlyingStreamHasNext = false;
                queue.enqueue(b);
                statusLast = LAST_WAS_OTHER;
                break;
            default:
                queue.enqueue(b);
                statusLast = LAST_WAS_OTHER;
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        if (underlyingStreamHasNext) {
            byte[] buffer = new byte[b.length];
            int read = in.read(buffer);
            if (read == -1) {
                underlyingStreamHasNext = false;
            }
            for (int j = 0; j < read; j++) {
                doEnqueue(buffer[j]);
            }
        }
        if (queue.isEmpty()) {
            return -1;
        }
        int i = 0;
        for (; i < b.length && !queue.isEmpty(); i++) {
            b[i] = (byte) queue.dequeueInt();
        }
        return i;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (underlyingStreamHasNext) {
            byte[] buffer = new byte[len];
            int read = in.read(buffer, 0, len);
            if (read == -1) {
                underlyingStreamHasNext = false;
            }
            for (int j = 0; j < read; j++) {
                doEnqueue(buffer[j]);
            }
        }
        if (queue.isEmpty()) {
            return -1;
        }
        int i = 0;
        for (; i < b.length && !queue.isEmpty(); i++) {
            b[off + i] = (byte) queue.dequeueInt();
        }
        return i;
    }
}
