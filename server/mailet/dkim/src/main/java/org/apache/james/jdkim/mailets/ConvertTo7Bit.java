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

import java.io.IOException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePart;

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

/**
 * Make sure the message stream is 7bit. Every 8bit part is encoded to
 * quoted-printable or base64 and the message is saved.
 */
public class ConvertTo7Bit extends GenericMailet {

    public void service(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();
        try {
            convertTo7Bit(message);
            message.saveChanges();
        } catch (IOException e) {
            throw new MessagingException("IOException converting message to 7bit: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a message to 7 bit.
     */
    private void convertTo7Bit(MimePart part) throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            MimeMultipart parts = (MimeMultipart) part.getContent();
            int count = parts.getCount();
            for (int i = 0; i < count; i++) {
                convertTo7Bit((MimePart) parts.getBodyPart(i));
            }
        } else if ("8bit".equals(part.getEncoding())) {
            // The content may already be in encoded the form (likely with mail
            // created from a stream). In that case, just changing the encoding
            // to quoted-printable will mangle the result when this is
            // transmitted.
            // We must first convert the content into its native format, set it
            // back, and only THEN set the transfer encoding to force the
            // content to be encoded appropriately.

            // if the part doesn't contain text it will be base64 encoded.
            String contentTransferEncoding = part.isMimeType("text/*") ? "quoted-printable" : "base64";
            part.setContent(part.getContent(), part.getContentType());
            part.setHeader("Content-Transfer-Encoding", contentTransferEncoding);
            part.addHeader("X-MIME-Autoconverted", "from 8bit to "
                    + contentTransferEncoding + " by "
                    + getMailetContext().getServerInfo());
        }
    }

}
