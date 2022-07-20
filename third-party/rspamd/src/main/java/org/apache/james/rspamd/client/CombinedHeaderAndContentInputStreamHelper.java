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

package org.apache.james.rspamd.client;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

public class CombinedHeaderAndContentInputStreamHelper {
    public static InputStream mergeHeaderAndContentInputStream(InputStream headerInputStream, InputStream contentInputStream) {
        return new SequenceInputStream(new SequenceInputStream(headerInputStream, new ByteArrayInputStream("\r\n".getBytes())), contentInputStream);
    }

    public static InputStream getInputStreamOfMessageHeaders(MimeMessage message) throws MessagingException {
        Enumeration<String> heads = message.getAllHeaderLines();
        StringBuilder headBuffer = new StringBuilder();
        while (heads.hasMoreElements()) {
            headBuffer.append(heads.nextElement()).append("\n");
        }
        return new ByteArrayInputStream(headBuffer.toString().getBytes(StandardCharsets.UTF_8));
    }
}
