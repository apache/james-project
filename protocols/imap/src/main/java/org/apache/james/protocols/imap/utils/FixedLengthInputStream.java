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

package org.apache.james.protocols.imap.utils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 
 * An input stream which reads a fixed number of bytes from the underlying input
 * stream. Once the number of bytes has been read, the FixedLengthInputStream
 * will act as thought the end of stream has been reached, even if more bytes
 * are present in the underlying input stream.
 */
public class FixedLengthInputStream extends FilterInputStream {
    private long pos = 0;

    private long length;

    public FixedLengthInputStream(InputStream in, long length) {
        super(in);
        this.length = length;
    }

    public int read() throws IOException {
        if (pos >= length) {
            return -1;
        }
        pos++;
        return super.read();
    }

    public int read(byte b[]) throws IOException {

        return read(b, 0, b.length);
    }

    public int read(byte b[], int off, int len) throws IOException {

        if (pos >= length) {
            return -1;
        }

        if (pos + len >= length) {
            int readLimit = (int) length - (int) pos;
            pos = length;

            return super.read(b, off, readLimit);
        }

        int i = super.read(b, off, len);
        pos += i;
        return i;

    }

    public long skip(long n) throws IOException {
        throw new IOException("Not implemented");
        // return super.skip( n );
    }

    public int available() throws IOException {
        return (int) (length - pos);
    }

    public void close() throws IOException {
        // Don't do anything to the underlying stream.
    }

    public void mark(int readlimit) {
        // Don't do anything.
    }

    public synchronized void reset() throws IOException {
        throw new IOException("mark not supported");
    }

    public boolean markSupported() {
        return false;
    }
}
