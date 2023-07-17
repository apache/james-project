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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.io.input.UnsynchronizedBufferedInputStream;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

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
@Experimental
public class RecoverAttachment extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecoverAttachment.class);
    @SuppressWarnings("unchecked")
    private static final Class<Map<String, AttributeValue<?>>> MAP_STRING_BYTES_CLASS = (Class<Map<String, AttributeValue<?>>>) (Object) Map.class;

    private static final String ATTRIBUTE_PARAMETER_NAME = "attribute";

    private AttributeName attributeName;

    @Override
    public void init() throws MailetException {
        String attributeNameRaw = getInitParameter(ATTRIBUTE_PARAMETER_NAME);

        if (attributeNameRaw == null) {
            throw new MailetException(ATTRIBUTE_PARAMETER_NAME
                    + " is a mandatory parameter");
        }

        LOGGER.debug("RecoverAttachment is initialised with attribute [{}]", attributeNameRaw);
        attributeName = AttributeName.of(attributeNameRaw);
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
    @Override
    public void service(Mail mail) throws MailetException {
        AttributeUtils
            .getValueAndCastFromMail(mail, attributeName, MAP_STRING_BYTES_CLASS)
            .ifPresent(Throwing.<Map<String, AttributeValue<?>>>consumer(attachments ->
                    processAttachment(mail, attachments)).sneakyThrow());
    }

    private void processAttachment(Mail mail, Map<String,  AttributeValue<?>> attachments) throws MailetException {
        MimeMessage message;
        try {
            message = mail.getMessage();
        } catch (MessagingException e) {
            throw new MailetException(
                    "Could not retrieve message from Mail object", e);
        }

        Iterator<AttributeValue<?>> i = attachments.values().iterator();
        try {
            while (i.hasNext()) {
                if (!(i.next().getValue() instanceof byte[])) {
                    continue;
                }
                byte[] bytes = (byte[]) i.next().getValue();
                InputStream is = UnsynchronizedBufferedInputStream.builder()
                    .setInputStream(new ByteArrayInputStream(bytes))
                    .get();
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
            LOGGER.error("MessagingException in recoverAttachment", e);
        } catch (IOException e) {
            LOGGER.error("IOException in recoverAttachment", e);
        }
    }

    @Override
    public String getMailetInfo() {
        return "RecoverAttachment Mailet";
    }

}
