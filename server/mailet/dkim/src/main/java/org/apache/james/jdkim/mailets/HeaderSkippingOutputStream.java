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

package org.apache.james.jdkim.mailets;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Ignore writes until the given sequence is found.
 */
public class HeaderSkippingOutputStream extends FilterOutputStream {

    private final byte[] skipTo = "\r\n\r\n".getBytes();
    private boolean inHeaders = true;
    private int pos = 0;

    public HeaderSkippingOutputStream(OutputStream out) {
        super(out);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        if (inHeaders) {
            for (int i = off; i < off + len; i++) {
                if (b[i] == skipTo[pos]) {
                    pos++;
                    if (pos == skipTo.length) {
                        inHeaders = false;
                        if (len - i - 1 > 0) {
                            out.write(b, i + 1, len - i - 1);
                        }
                        break;
                    }
                } else {
                    pos = 0;
                }
            }
        } else {
            out.write(b, off, len);
        }
    }

    public void write(int b) throws IOException {
        if (inHeaders) {
            if (skipTo[pos] == b) {
                pos++;
                if (pos == skipTo.length) {
                    inHeaders = false;
                }
            } else {
                pos = 0;
            }
        } else {
            out.write(b);
        }
    }

}
