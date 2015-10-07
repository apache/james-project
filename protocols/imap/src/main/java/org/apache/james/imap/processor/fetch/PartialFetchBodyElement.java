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

package org.apache.james.imap.processor.fetch;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.james.imap.message.response.FetchResponse.BodyElement;

/**
 * Wraps full content to implement a partial fetch.
 */
final class PartialFetchBodyElement implements BodyElement {

    private final BodyElement delegate;

    private final long firstOctet;

    private final long numberOfOctets;

    private final String name;

    public PartialFetchBodyElement(final BodyElement delegate, final long firstOctet, final long numberOfOctets) {
        super();
        this.delegate = delegate;
        this.firstOctet = firstOctet;
        this.numberOfOctets = numberOfOctets;
        name = delegate.getName() + "<" + firstOctet + ">";
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.BodyElement#getName()
     */
    public String getName() {
        return name;
    }

    /**
     * @see org.apache.james.imap.message.response.Literal#size()
     */
    public long size() throws IOException {
        final long size = delegate.size();
        final long lastOctet = this.numberOfOctets + firstOctet;
        final long result;
        if (firstOctet > size) {
            result = 0;
        } else if (size > lastOctet) {
            result = numberOfOctets;
        } else {
            result = size - firstOctet;
        }
        return result;
    }

    /**
     * @see org.apache.james.imap.message.response.Literal#getInputStream()
     */
    public InputStream getInputStream() throws IOException {
        return new LimitingInputStream(delegate.getInputStream(), firstOctet, size());
    }

    private final class LimitingInputStream extends FilterInputStream {
        private long pos = 0;
        private long length;
        private long offset;

        public LimitingInputStream(InputStream in, long offset, long length) {
            super(in);
            this.length = length;
            this.offset = offset;
        }

        /**
         * Check if the offset was reached. If not move the wrapped
         * {@link InputStream} to the needed offset
         * 
         * @throws IOException
         */
        private void checkOffset() throws IOException {
            if (offset > -1) {
                // first try to skip on the InputStream as it is mostly faster
                // the calling read in a loop
                try {
                    offset -= in.skip(offset);
                } catch (IOException e) {
                    // maybe because skip is not supported
                }
                while (offset > 0) {
                    // consume the stream till we reach the offset
                    in.read();
                    offset--;
                }
                offset = -1;
            }
        }

        /**
         * @see java.io.FilterInputStream#read()
         */
        public int read() throws IOException {
            checkOffset();
            if (pos >= length) {
                return -1;
            }
            pos++;
            return super.read();
        }

        /**
         * @see java.io.FilterInputStream#read(byte[])
         */
        public int read(byte b[]) throws IOException {
            return read(b, 0, b.length);
        }

        /**
         * @see java.io.FilterInputStream#read(byte[], int, int)
         */
        public int read(byte b[], int off, int len) throws IOException {
            checkOffset();

            if (pos >= length) {
                return -1;
            }
            int readLimit;
            if (pos + len >= length) {
                readLimit = (int) length - (int) pos;
            } else {
                readLimit = len;
            }

            int i = super.read(b, off, readLimit);
            pos += i;
            return i;

        }

        /**
         * Throws {@link IOException}
         */
        public long skip(long n) throws IOException {
            throw new IOException("Not implemented");
        }

        /**
         * @see java.io.FilterInputStream#available()
         */
        public int available() throws IOException {
            // Correctly calculate in available bytes.
            // See IMAP-295
            checkOffset();
            int i = in.available();
            if (i == -1) {
                return -1;
            } else {
                if (i >= length) {
                    return (int) length - (int) pos;
                } else {
                    return i;
                }
            }
        }

        public void mark(int readlimit) {
            // Don't do anything.
        }

        public void reset() throws IOException {
            throw new IOException("mark not supported");
        }

        /**
         * Return false as mark is not supported
         */
        public boolean markSupported() {
            return false;
        }
    }

}