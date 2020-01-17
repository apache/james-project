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

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.NotImplementedException;

public class SizeInputStream extends InputStream {
    private final InputStream wrapped;
    private long size;

    public SizeInputStream(InputStream wrapped) {
        this.wrapped = wrapped;
        this.size = 0L;
    }

    @Override
    public int read() throws IOException {
        int read = wrapped.read();

        if (read > 0) {
            size++;
        }

        return read;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int read = wrapped.read(b);
        return increaseSize(read);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int read = wrapped.read(b, off, len);
        return increaseSize(read);
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = wrapped.skip(n);
        return increaseSize(skipped);
    }

    @Override
    public int available() throws IOException {
        return wrapped.available();
    }

    @Override
    public void close() throws IOException {
        wrapped.close();
    }

    @Override
    public void mark(int readlimit) {
        throw new NotImplementedException("'mark' is not supported'");
    }

    @Override
    public void reset() {
        throw new NotImplementedException("'reset' is not supported'");
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    public long getSize() {
        return size;
    }

    private <T extends Number> T increaseSize(T chunkSize) {
        long longValue = chunkSize.longValue();
        if (longValue > 0) {
            size += longValue;
        }
        return chunkSize;
    }
}
