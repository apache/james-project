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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * This {@link FilterInputStream} makes sure that the last chars of the stream
 * are \r\n
 * 
 * See JAMES-1174 for an use case
 */
public class CRLFTerminatedInputStream extends FilterInputStream {

    private int previousLast;
    private int last;
    private byte[] extraData;
    private int pos = 0;
    private boolean complete = false;

    private boolean endOfStream = false;

    public CRLFTerminatedInputStream(InputStream in) {
        super(in);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (endOfStream == false) {

            int r = in.read(b, off, len);
            if (r == -1) {
                endOfStream = true;
                calculateExtraData();

                return fillArray(b, off, len);
            } else {
                if (r == 1) {
                    previousLast = last;
                    last = b[off + r - 1];
                } else {
                    // Make sure we respect the offset. Otherwise it could let the RETRCmdHandler
                    // hang forever. See JAMES-1222
                    last = b[off + r - 1];
                    if (off + r - 2 >= 0) {
                        previousLast = b[off + r - 2];
                    } else {
                        previousLast = -1;
                    }
                }
                return r;
            }
        } else {
            return fillArray(b, off, len);
        }
    }

    private int fillArray(byte[] b, int off, int len) {
        int a = -1;
        int i = 0;
        if (complete) {
            return -1;
        }
        while (i < len) {
            a = readNext();
            if (a == -1) {
                complete = true;
                break;
            } else {
                b[off + i++] = (byte) a;

            }
        }
        return i;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read() throws IOException {
        if (endOfStream == false) {
            int i = super.read();
            if (i == -1) {
                endOfStream = true;
                calculateExtraData();
                return readNext();
            } else {
                previousLast = last;
                last = i;
            }
            return i;

        } else {
            return readNext();
        }
    }

    private void calculateExtraData() {
        if (last == '\n') {
            if (previousLast == '\r') {
                extraData = null;
            } else {
                extraData = new byte[2];
                extraData[0] = '\r';
                extraData[1] = '\n';
            }
        } else if (last == '\r') {
            extraData = new byte[1];
            extraData[0] = '\n';
        } else {
            extraData = new byte[2];
            extraData[0] = '\r';
            extraData[1] = '\n';
        }

    }

    private int readNext() {
        if (extraData == null || extraData.length == pos) {
            return -1;
        } else {
            return extraData[pos++];
        }
    }
}
