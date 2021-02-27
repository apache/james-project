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

package org.apache.james.mailbox.model;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

import org.apache.james.mailbox.exception.MailboxException;

import com.google.common.io.ByteSource;
import com.google.common.io.FileBackedOutputStream;

public class ByteSourceContent implements Content, Closeable {
    private static final int FILE_THRESHOLD = 1024 * 100;

    public static ByteSourceContent of(InputStream stream) throws IOException {
        FileBackedOutputStream out = new FileBackedOutputStream(FILE_THRESHOLD);
        try {
            stream.transferTo(out);
            return new ByteSourceContent(out.asByteSource(), out::close);
        } catch (IOException ioException) {
            out.close();
            throw ioException;
        }
    }

    private final ByteSource byteSource;
    private final Closeable closeable;

    public ByteSourceContent(ByteSource byteSource, Closeable closeable) {
        this.byteSource = byteSource;
        this.closeable = closeable;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return byteSource.openStream();
    }

    @Override
    public long size() throws MailboxException {
        try {
            return byteSource.size();
        } catch (IOException ioException) {
            throw new MailboxException("Cannot compute size", ioException);
        }
    }

    @Override
    public void close() throws IOException {
        closeable.close();
    }
}
