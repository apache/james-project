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

import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.james.imap.decode.ImapRequestLineReader;

/**
 * {@link FileInputStream} which call the eol() method of the
 * {@link ImapRequestLineReader} when the end of the wrapped {@link InputStream}
 * is reached
 */
public class EolInputStream extends FilterInputStream {

    private final ImapRequestLineReader reader;
    private boolean eolCalled = false;

    public EolInputStream(ImapRequestLineReader reader, InputStream in) {
        super(in);
        this.reader = reader;
    }

    @Override
    public int read() throws IOException {
        int i = in.read();
        eol(i);
        return i;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int i = in.read(b, off, len);
        eol(i);
        return i;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int i = in.read(b);
        eol(i);
        return i;
    }

    private void eol(int i) throws IOException {
        if (i == -1 && eolCalled == false) {
            reader.eol();
            eolCalled = true;
        }
    }

    @Override
    public int available() throws IOException {
        return in.available();
    }

}
