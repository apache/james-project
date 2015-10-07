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
package org.apache.james.protocols.imap;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Iterator;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.protocols.imap.utils.EolInputStream;
import org.apache.james.protocols.imap.utils.FixedLengthInputStream;

public class IMAPRequestLineReader extends ImapRequestLineReader{

    private final Iterator<ByteBuffer> args;
    private final byte[] prefix;
    private int pos = 0;
    private ByteBuffer curBuf;
    
    public IMAPRequestLineReader(IMAPRequest request) throws UnsupportedEncodingException {
        this.args = request.getArguments();
        prefix = (request.getTag() + " " + request.getCommand() + " ").getBytes("US-ASCII");
    }
    
    @Override
    public char nextChar() throws DecodingException {
        if (pos >= prefix.length) {
            if (curBuf == null || curBuf.remaining() == 0) {
                if (args.hasNext()) {
                    curBuf = args.next();
                } else {
                    throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unexpected end of stream.");
                }
            }
            return (char) curBuf.get();
        }
        return (char) prefix[pos++];
    }

    @Override
    public InputStream read(int size, boolean extraCRLF) throws DecodingException {
        // Unset the next char.
        nextSeen = false;
        nextChar = 0;
        FixedLengthInputStream in = new FixedLengthInputStream(new InputStream() {
            
            @Override
            public int read() throws IOException {
                return consume();
            }
        }, size);
        if (extraCRLF) {
            return new EolInputStream(this, in);
        } else {
            return in;
        }
    }

    @Override
    protected void commandContinuationRequest() throws DecodingException {
        // TODO FIX ME!
        
    }

}
