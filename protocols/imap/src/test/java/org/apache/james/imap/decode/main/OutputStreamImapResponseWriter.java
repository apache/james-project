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

package org.apache.james.imap.decode.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.james.imap.encode.ImapResponseWriter;
import org.apache.james.imap.message.response.Literal;

/**
 * Class providing methods to send response messages from the server to the
 * client.
 */
public class OutputStreamImapResponseWriter implements ImapResponseWriter {

    private final OutputStream output;

    public OutputStreamImapResponseWriter(OutputStream output) {
        this.output = output;
    }

    public void flush() throws IOException {
        output.flush();
    }



    /**
     * @see
     * org.apache.james.imap.encode.ImapResponseWriter#write(org.apache.james.imap.message.response.Literal)
     */
    public void write(Literal literal) throws IOException {
        InputStream in = null;
        try {
            in = literal.getInputStream();

            byte[] buffer = new byte[1024];
            for (int len; (len = in.read(buffer)) != -1;) {
                output.write(buffer, 0, len);
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }

    }

    /**
     * @see org.apache.james.imap.encode.ImapResponseWriter#write(byte[])
     */
    public void write(byte[] buffer) throws IOException {
        output.write(buffer);
    }

}
