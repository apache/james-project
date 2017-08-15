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
import java.io.Serializable;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.commons.io.IOUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

/**
 * This mailet replace the mail attribute map of key to MimePart
 * by a map of key to the MimePart content (as bytes).
 * <br />
 * It takes only one parameter:
 * <ul>
 * <li>attribute (mandatory): mime content to be decoded, expected to be a Map&lt;String, byte[]&gt;
 * </ul>
 *
 * Then all this map attribute values will be replaced by their content.
 */
public class MimeDecodingMailet extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(MimeDecodingMailet.class);

    public static final String ATTRIBUTE_PARAMETER_NAME = "attribute";

    private String attribute;

    @Override
    public void init() throws MessagingException {
        attribute = getInitParameter(ATTRIBUTE_PARAMETER_NAME);
        if (Strings.isNullOrEmpty(attribute)) {
            throw new MailetException("No value for " + ATTRIBUTE_PARAMETER_NAME
                    + " parameter was provided.");
        }
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        if (mail.getAttribute(attribute) == null) {
            return;
        }

        ImmutableMap.Builder<String, byte[]> extractedMimeContentByName = ImmutableMap.builder();
        for (Map.Entry<String, byte[]> entry: getAttributeContent(mail).entrySet()) {
            Optional<byte[]> maybeContent = extractContent(entry.getValue());
            if (maybeContent.isPresent()) {
                extractedMimeContentByName.put(entry.getKey(), maybeContent.get());
            }
        }
        mail.setAttribute(attribute, extractedMimeContentByName.build());
    }

    @SuppressWarnings("unchecked")
    private Map<String, byte[]> getAttributeContent(Mail mail) throws MailetException {
        Serializable attributeContent = mail.getAttribute(attribute);
        if (! (attributeContent instanceof Map)) {
            LOGGER.debug("Invalid attribute found into attribute "
                    + attribute + "class Map expected but "
                    + attributeContent.getClass() + " found.");
            return ImmutableMap.of();
        }
        return (Map<String, byte[]>) attributeContent;
    }

    private Optional<byte[]> extractContent(Object rawMime) throws MessagingException {
        try {
            MimeBodyPart mimeBodyPart = new MimeBodyPart(new ByteArrayInputStream((byte[]) rawMime));
            return Optional.fromNullable(IOUtils.toByteArray(mimeBodyPart.getInputStream()));
        } catch (IOException e) {
            LOGGER.error("Error while extracting content from mime part", e);
            return Optional.absent();
        } catch (ClassCastException e) {
            LOGGER.error("Invalid map attribute types.", e);
            return Optional.absent();
        }
    }

    @Override
    public String getMailetInfo() {
        return "MimeDecodingMailet";
    }

}
