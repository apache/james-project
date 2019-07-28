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

package org.apache.james.blob.cassandra.utils;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class PipedInputStreamHandlingError extends PipedInputStream {
    private final AtomicReference<RuntimeException> error;

    public PipedInputStreamHandlingError() {
        super();
        this.error = new AtomicReference<>();
    }

    void setError(RuntimeException e) {
        error.set(e);
    }

    private void assertNoError() {
        Optional<RuntimeException> maybeError = Optional.ofNullable(error.get());
        if (maybeError.isPresent()) {
            throw maybeError.get();
        }
    }

    @Override
    public void connect(PipedOutputStream src) throws IOException {
        assertNoError();
        super.connect(src);
    }

    @Override
    protected synchronized void receive(int b) throws IOException {
        assertNoError();
        super.receive(b);
    }

    @Override
    public synchronized int read() throws IOException {
        assertNoError();
        return super.read();
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) throws IOException {
        assertNoError();
        return super.read(b, off, len);
    }

    @Override
    public synchronized int available() throws IOException {
        assertNoError();
        return super.available();
    }

    @Override
    public void close() throws IOException {
        try {
            assertNoError();
        } finally {
            super.close();
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        assertNoError();
        return super.read(b);
    }

    @Override
    public long skip(long n) throws IOException {
        assertNoError();
        return super.skip(n);
    }

    @Override
    public synchronized void mark(int readlimit) {
        assertNoError();
        super.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        assertNoError();
        super.reset();
    }

    @Override
    public boolean markSupported() {
        assertNoError();
        return super.markSupported();
    }
}
