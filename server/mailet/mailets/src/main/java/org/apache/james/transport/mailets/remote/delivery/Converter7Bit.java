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

package org.apache.james.transport.mailets.remote.delivery;

import java.io.IOException;
import java.util.List;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePart;

import org.apache.james.javax.MultipartUtil;
import org.apache.mailet.MailetContext;

public class Converter7Bit {

    private final MailetContext mailetContext;

    public Converter7Bit(MailetContext mailetContext) {
        this.mailetContext = mailetContext;
    }

    public MimePart convertTo7Bit(MimePart part) throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            List<BodyPart> bodyParts = MultipartUtil.retrieveBodyParts((MimeMultipart) part.getContent());
            for (BodyPart bodyPart : bodyParts) {
                convertTo7Bit((MimePart) bodyPart);
            }
        } else if ("8bit".equals(part.getEncoding())) {
            // The content may already be in encoded the form (likely with mail
            // created from a
            // stream). In that case, just changing the encoding to
            // quoted-printable will mangle
            // the result when this is transmitted. We must first convert the
            // content into its
            // native format, set it back, and only THEN set the transfer
            // encoding to force the
            // content to be encoded appropriately.

            // if the part doesn't contain text it will be base64 encoded.
            String contentTransferEncoding = part.isMimeType("text/*") ? "quoted-printable" : "base64";
            part.setContent(part.getContent(), part.getContentType());
            part.setHeader("Content-Transfer-Encoding", contentTransferEncoding);
            part.addHeader("X-MIME-Autoconverted", "from 8bit to " + contentTransferEncoding + " by " + mailetContext.getServerInfo());
        }
        return part;
    }

}
