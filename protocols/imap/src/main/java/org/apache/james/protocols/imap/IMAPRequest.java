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

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;

import org.apache.james.protocols.api.Request;

public class IMAPRequest implements Request {

    private static final String US_ASCII = "US_ASCII";
    
    private static final String CRLF = "\r\n";
    private final Collection<ByteBuffer> lines;
    private final String tag;
    private final String command;
    
    public IMAPRequest(Collection<ByteBuffer> lines) {
        this.lines = lines;
        ByteBuffer buf = lines.iterator().next();
        buf.rewind();
        
        this.tag = read(buf);
        this.command = read(buf).toUpperCase(Locale.US);
    }
    
    public IMAPRequest(ByteBuffer line) {
        this(Arrays.asList(line));
    }
    
    private String read(ByteBuffer buf) {
        StringBuilder sb = new StringBuilder();
        int i;
        while ((i = buf.get()) != ' ') {
            sb.append((byte) i);
        }
        return sb.toString();
    }
    
    /**
     * Return the tag of the request
     * 
     * @return tag
     */
    public String getTag() {
        return tag;
    }
    
    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.Request#getArgument()
     */
    public String getArgument() {
        int tagOffeset = tag.length() + command.length() + 2;
        StringBuilder sb = new StringBuilder();
        Iterator<ByteBuffer> linesIt = lines.iterator();
        
        while (linesIt.hasNext()){
            ByteBuffer line = linesIt.next();
            byte[] buf;
            if (line.hasArray()) {
                buf = line.array();
            } else {
                line.rewind();
                buf = new byte[line.remaining() - tagOffeset];
                line.get(buf, tagOffeset, line.remaining());
            }
            try {
                sb.append(new String(buf, US_ASCII));
            } catch (UnsupportedEncodingException e) {
                // Should never happend
                e.printStackTrace();
            }
            if (linesIt.hasNext()) {
                sb.append(CRLF);
            }
        }
        return sb.toString();
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.Request#getCommand()
     */
    public String getCommand() {
        return command;
    }
    
    /**
     * Return an {@link Iterator} which holds all argument lines. The returned {@link ByteBuffer}'s will be 
     * rewind by calling {@link ByteBuffer#rewind()} before return them
     * 
     * @return arguments
     */
    public Iterator<ByteBuffer> getArguments() {
        return new Iterator<ByteBuffer>() {
            boolean first = true;
            Iterator<ByteBuffer> buffIt = lines.iterator();

            public boolean hasNext() {
                return buffIt.hasNext();
            }

            public ByteBuffer next() {
                ByteBuffer buf = buffIt.next();
                buf.rewind();

                if (first) {
                    first = false;
                    buf.position(getTag().length() + getCommand().length() + 2);
                    buf = buf.slice();
                }
                return buf;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

}
