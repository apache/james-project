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
/**
 * 
 */
package org.apache.james.mailbox.store.streaming;

import java.io.IOException;
import java.io.InputStream;

/**
 * {@link InputStream} implementation which just consume the the wrapped {@link InputStream} and count
 * the lines which are contained within the wrapped stream
 * 
 *
 */
final public class CountingInputStream extends InputStream {

    private final InputStream in;

    private int lineCount;

    private int octetCount;

    public CountingInputStream(InputStream in) {
        super();
        this.in = in;
    }

    /**
     * @see java.io.InputStream#read()
     */
    public int read() throws IOException {
        int next = in.read();
        if (next > 0) {
            octetCount++;
            if (next == '\r') {
                lineCount++;
            }
        }
        return next;
    }

    /**
     * Return the line count 
     * 
     * @return lineCount
     */
    public int getLineCount() {
        return lineCount;
    }

    /**
     * Return the octet count
     * 
     * @return octetCount
     */
    public int getOctetCount() {
        return octetCount;
    }
    
    /**
     * Reads - and discards - the rest of the stream
     * @throws IOException
     */
    public void readAll() throws IOException {
        while (read()>0);
    }
}
