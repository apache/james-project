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

package org.apache.james.server.core;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import jakarta.mail.internet.InternetHeaders;

/**
 * Provide an {@link InputStream} over an {@link InternetHeaders} instance. When
 * the end of {@link InternetHeaders} are reached a {@link #LINE_SEPERATOR} is
 * append
 */
public class InternetHeadersInputStream extends InputStream {

    private static final String LINE_SEPERATOR = "\r\n";

    private final Enumeration<String> headerLines;
    private byte[] currLine;
    private int pos = 0;

    public InternetHeadersInputStream(InternetHeaders headers) {
        this(headers.getAllHeaderLines());
    }

    public InternetHeadersInputStream(Enumeration<String> headerLines) {
        this.headerLines = headerLines;
    }

    @Override
    public int read() {
        if (currLine == null || pos == currLine.length) {
            if (!readNextLine()) {
                return -1;
            }
        }
        return currLine[pos++];
    }

    /**
     * Load the next header line if possible
     * 
     * @return true if there was an headerline which could be read
     */
    private boolean readNextLine() {
        if (headerLines.hasMoreElements()) {
            pos = 0;
            String line = (headerLines.nextElement() + LINE_SEPERATOR);
            // Add seperator to show that headers are complete
            if (!headerLines.hasMoreElements()) {
                line += LINE_SEPERATOR;
            }
            currLine = line.getBytes(StandardCharsets.US_ASCII);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void close() {
        currLine = null;
    }

}
