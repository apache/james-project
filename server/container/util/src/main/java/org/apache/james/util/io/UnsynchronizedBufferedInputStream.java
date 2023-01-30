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

package org.apache.james.util.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Copied from {@link java.io.BufferedInputStream} with the following modifications:
 *  - Removal of 'synchronized' keyword
 *  - Removal of 'volatile' keyword
 *  - Removal of Unsafe usages
 *
 *  See https://issues.apache.org/jira/projects/IO/issues/IO-786 for rationals
 */
public class UnsynchronizedBufferedInputStream extends FilterInputStream {
    private static int DEFAULT_BUFFER_SIZE = 8192;
    private static int MAX_BUFFER_SIZE = 2147483639;
    protected byte[] buf;
    protected int count;
    protected int pos;
    protected int markpos;
    protected int marklimit;

    private InputStream getInIfOpen() throws IOException {
        InputStream input = this.in;
        if (input == null) {
            throw new IOException("Stream closed");
        } else {
            return input;
        }
    }

    private byte[] getBufIfOpen() throws IOException {
        byte[] buffer = this.buf;
        if (buffer == null) {
            throw new IOException("Stream closed");
        } else {
            return buffer;
        }
    }

    public UnsynchronizedBufferedInputStream(InputStream in) {
        this(in, DEFAULT_BUFFER_SIZE);
    }

    public UnsynchronizedBufferedInputStream(InputStream in, int size) {
        super(in);
        this.markpos = -1;
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        } else {
            this.buf = new byte[size];
        }
    }

    private void fill() throws IOException {
        byte[] buffer = this.getBufIfOpen();
        int nsz;
        if (this.markpos < 0) {
            this.pos = 0;
        } else if (this.pos >= buffer.length) {
            if (this.markpos > 0) {
                nsz = this.pos - this.markpos;
                System.arraycopy(buffer, this.markpos, buffer, 0, nsz);
                this.pos = nsz;
                this.markpos = 0;
            } else if (buffer.length >= this.marklimit) {
                this.markpos = -1;
                this.pos = 0;
            } else {
                if (buffer.length >= MAX_BUFFER_SIZE) {
                    throw new OutOfMemoryError("Required array size too large");
                }

                nsz = this.pos <= MAX_BUFFER_SIZE - this.pos ? this.pos * 2 : MAX_BUFFER_SIZE;
                if (nsz > this.marklimit) {
                    nsz = this.marklimit;
                }

                byte[] nbuf = new byte[nsz];
                System.arraycopy(buffer, 0, nbuf, 0, this.pos);

                buffer = nbuf;
            }
        }

        this.count = this.pos;
        nsz = this.getInIfOpen().read(buffer, this.pos, buffer.length - this.pos);
        if (nsz > 0) {
            this.count = nsz + this.pos;
        }

    }

    public int read() throws IOException {
        if (this.pos >= this.count) {
            this.fill();
            if (this.pos >= this.count) {
                return -1;
            }
        }

        return this.getBufIfOpen()[this.pos++] & 255;
    }

    private int read1(byte[] b, int off, int len) throws IOException {
        int avail = this.count - this.pos;
        if (avail <= 0) {
            if (len >= this.getBufIfOpen().length && this.markpos < 0) {
                return this.getInIfOpen().read(b, off, len);
            }

            this.fill();
            avail = this.count - this.pos;
            if (avail <= 0) {
                return -1;
            }
        }

        int cnt = avail < len ? avail : len;
        System.arraycopy(this.getBufIfOpen(), this.pos, b, off, cnt);
        this.pos += cnt;
        return cnt;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        this.getBufIfOpen();
        if ((off | len | off + len | b.length - (off + len)) < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        } else {
            int n = 0;

            InputStream input;
            do {
                int nread = this.read1(b, off + n, len - n);
                if (nread <= 0) {
                    return n == 0 ? nread : n;
                }

                n += nread;
                if (n >= len) {
                    return n;
                }

                input = this.in;
            } while (input == null || input.available() > 0);

            return n;
        }
    }

    public long skip(long n) throws IOException {
        this.getBufIfOpen();
        if (n <= 0L) {
            return 0L;
        } else {
            long avail = (long)(this.count - this.pos);
            if (avail <= 0L) {
                if (this.markpos < 0) {
                    return this.getInIfOpen().skip(n);
                }

                this.fill();
                avail = (long)(this.count - this.pos);
                if (avail <= 0L) {
                    return 0L;
                }
            }

            long skipped = avail < n ? avail : n;
            this.pos = (int)((long)this.pos + skipped);
            return skipped;
        }
    }

    public int available() throws IOException {
        int n = this.count - this.pos;
        int avail = this.getInIfOpen().available();
        return n > 2147483647 - avail ? 2147483647 : n + avail;
    }

    public void mark(int readlimit) {
        this.marklimit = readlimit;
        this.markpos = this.pos;
    }

    public void reset() throws IOException {
        this.getBufIfOpen();
        if (this.markpos < 0) {
            throw new IOException("Resetting to invalid mark");
        } else {
            this.pos = this.markpos;
        }
    }

    public boolean markSupported() {
        return true;
    }

    public void close() throws IOException {
        while (true) {
            byte[] buffer;
            if ((buffer = this.buf) != null) {

                InputStream input = this.in;
                this.in = null;
                if (input != null) {
                    input.close();
                }

                return;
            }

            return;
        }
    }
}
