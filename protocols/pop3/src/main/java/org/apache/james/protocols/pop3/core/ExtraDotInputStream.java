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

package org.apache.james.protocols.pop3.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;


/**
 * Adds extra dot if dot occurs in message body at beginning of line (according
 * to RFC1939)
 */
public class ExtraDotInputStream extends InputStream {

    boolean startLine = true;
    private int last;
    private final PushbackInputStream in;

    public ExtraDotInputStream(InputStream in) {
        this.in = new PushbackInputStream(in, 2);
        startLine = true;
    }
    
    @Override
    public int read() throws IOException {
       int i = in.read();
       if (startLine) {
           startLine = false;
           if (i == '.') {
                in.unread(i);
               return '.';
           }
           
       }
      
       if (last == '\r' && i == '\n') {
           startLine = true;
       }
       last = i;
       return i;
       
    }

    @Override
    public long skip(long n) throws IOException {
        throw new IOException("Skip not supported");
    }

    @Override
    public int available() throws IOException {
        return in.available();
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    @Override
    public void mark(int readlimit) {
        // not supported
    }

    @Override
    public void reset() throws IOException {
        // not supported
    }

    @Override
    public boolean markSupported() {
        return false;
    }

}
