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
import java.io.PushbackInputStream;

/**
 * {@link InputStream} which helps to keep track of the BodyOffset of the wrapped
 * {@link InputStream}
 *  
 *  IMPORTANT: This class is not thread-safe!
 *
 */
public class BodyOffsetInputStream extends InputStream {
    private long count = 0;
    private long bodyStartOctet = -1;
    private final PushbackInputStream in;
    private long readBytes = 0;

    public BodyOffsetInputStream(InputStream in) {
        // we need to pushback at max 3 bytes
        this.in = new PushbackInputStream(in, 3);
    }

    @Override
    public int read() throws IOException {
        int i = in.read();
        if (i != -1) {
            readBytes++;
            if (bodyStartOctet == -1 && i == 0x0D) {
                int a = in.read();
                if (a == 0x0A) {
                    int b = in.read();

                    if (b == 0x0D) {
                        int c = in.read();

                        if (c == 0x0A) {
                            bodyStartOctet = count + 4;
                        }
                        in.unread(c);
                    }
                    in.unread(b);
                }
                in.unread(a);
            }
            count++;
        }
        return i;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (bodyStartOctet == -1) {
            return super.read(b, off, len);
        } else {
            int r = in.read(b, off, len);
            if (r != -1) {
                readBytes += r;
            }
            return r;
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        if (bodyStartOctet == -1) {
            return super.read(b);
        } else {
            int r = in.read(b);
            if (r != -1) {
                readBytes += r;
            }
            return r;
        }
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
        
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void reset() throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public long skip(long n) throws IOException {
        long i = 0; 
        while (i++ < n) {
            if (read() == -1) {
                break;
            }
        }
        return i;
    }
    
    /**
     * Return the bodyStartOffset or -1 if it could not be found. 
     * Be aware you can only expect some valid result from the method
     * if you have consumed the whole InputStream or if you are
     * sure that you reached the body
     * 
     * @return offset
     */
    public long getBodyStartOffset() {
        return bodyStartOctet;
    }
    
    /**
     * Return the read bytes so far
     * 
     * @return readBytes
     */
    public long getReadBytes() {
        return readBytes;
    }
    
}
