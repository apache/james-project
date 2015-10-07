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

package org.apache.james.transport.mailets;

import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

/**
 * <p>
 * This mailet takes an attachment stored in an attribute and attach it back to
 * the message
 * </p>
 * <p>
 * This may be used to place back attachment stripped by StripAttachment and
 * stored in the attribute
 * <code>org.apache.james.mailet.standard.mailets.StripAttachment.saved</code>
 * </p>
 * <p>
 * 
 * <pre>
 *   &lt;mailet match=&quot;All&quot; class=&quot;RecoverAttachment&quot; &gt;
 *     &lt;attribute&gt;my.attribute.name&lt;/attribute&gt;
 *   &lt;/mailet &gt;
 * </pre>
 * 
 * </p>
 */
public class RecoverAttachment extends GenericMailet {

    public static final String ATTRIBUTE_PARAMETER_NAME = "attribute";

    private String attributeName = null;

    /**
     * Checks if the mandatory parameters are present
     * 
     * @throws MailetException
     */
    public void init() throws MailetException {
        attributeName = getInitParameter(ATTRIBUTE_PARAMETER_NAME);

        if (attributeName == null) {
            throw new MailetException(ATTRIBUTE_PARAMETER_NAME
                    + " is a mandatory parameter");
        }

        log("RecoverAttachment is initialised with attribute [" + attributeName
                + "]");
    }

    /**
     * Service the mail: check for the attribute and attach the attachment to
     * the mail.
     * 
     * @param mail
     *            The mail to service
     * @throws MailetException
     *             Thrown when an error situation is encountered.
     */
    public void service(Mail mail) throws MailetException {
        @SuppressWarnings("unchecked")
        Map<String, byte[]> attachments = (Map<String, byte[]>) mail.getAttribute(attributeName);
        if (attachments != null) {

            MimeMessage message;
            try {
                message = mail.getMessage();
            } catch (MessagingException e) {
                throw new MailetException(
                        "Could not retrieve message from Mail object", e);
            }

            Iterator<byte[]> i = attachments.values().iterator();
            try {
                while (i.hasNext()) {
                    byte[] bytes = i.next();
                    InputStream is = new BufferedInputStream(
                            new ByteArrayInputStream(bytes));
                    MimeBodyPart p = new MimeBodyPart(is);
                    if (!(message.isMimeType("multipart/*") && (message
                            .getContent() instanceof MimeMultipart))) {
                        Object content = message.getContent();
                        String contentType = message.getContentType();
                        MimeMultipart mimeMultipart = new MimeMultipart();
                        message.setContent(mimeMultipart);
                        // This saveChanges is required when the MimeMessage has
                        // been created from
                        // an InputStream, otherwise it is not saved correctly.
                        message.saveChanges();
                        mimeMultipart.setParent(message);
                        MimeBodyPart bodyPart = new MimeBodyPart();
                        mimeMultipart.addBodyPart(bodyPart);
                        bodyPart.setContent(content, contentType);
                    }
                    ((MimeMultipart) message.getContent()).addBodyPart(p);
                }
                message.saveChanges();
            } catch (MessagingException e) {
                log("MessagingException in recoverAttachment", e);
            } catch (IOException e) {
                log("IOException in recoverAttachment", e);
            }
        }
    }

    /**
     * returns a String describing this mailet.
     * 
     * @return A desciption of this mailet
     */
    public String getMailetInfo() {
        return "RecoverAttachment Mailet";
    }

}
