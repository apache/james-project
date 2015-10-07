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
package org.apache.james.mailbox.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;

public class StringBuilderChannel implements WritableByteChannel {

    private static final Charset ASCII = Charset.forName("US-ASCII");
    
    public final StringBuilder builder = new StringBuilder(1024);
    
    public boolean isClosed = false;
    
    public int write(ByteBuffer src) throws IOException {
        final int result = src.limit() - src.position();
        builder.append(ASCII.decode(src));
        return result;
    }

    public void close() throws IOException {
        isClosed = true;
    }

    public boolean isOpen() {
        return !isClosed;
    }
    
    public String toString() {
        return builder.toString();
    }
}
