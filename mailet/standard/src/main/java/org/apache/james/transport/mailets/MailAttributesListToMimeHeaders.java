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

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

/**
 * <p>Convert attributes of type Collection&lt;String&gt; to headers</p>
 *
 * <p>Sample configuration:</p>
 * <pre><code>
 * &lt;mailet match="All" class="MailAttributesToMimeHeaders"&gt;
 * &lt;simplemapping&gt;org.apache.james.attribute1;
 * headerName1&lt;/simplemapping&gt;
 * &lt;simplemapping&gt;org.apache.james.attribute2;
 * headerName2&lt;/simplemapping&gt; &lt;/mailet&gt;
 * </code></pre>
 */
public class MailAttributesListToMimeHeaders extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailAttributesListToMimeHeaders.class);

    private Map<AttributeName, String> attributeNameToHeader;

    @Override
    public void init() throws MessagingException {
        String simpleMappings = getInitParameter("simplemapping");
        if (Strings.isNullOrEmpty(simpleMappings)) {
            throw new MessagingException("simplemapping is required");
        }

        attributeNameToHeader = MappingArgument
            .parse(simpleMappings)
            .entrySet()
            .stream()
            .collect(ImmutableMap.toImmutableMap(
                entry -> AttributeName.of(entry.getKey()),
                Entry::getValue));
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();
        attributeNameToHeader.entrySet()
            .forEach(entry -> addAttributeToHeader(mail, message, entry));
        message.saveChanges();
    }

    private void addAttributeToHeader(Mail mail, MimeMessage message, Entry<AttributeName, String> entry) {
        AttributeUtils
            .getAttributeValueFromMail(mail, entry.getKey())
            .ifPresent(attribute -> {
                if (attribute instanceof Collection) {
                    @SuppressWarnings("unchecked")
                    Collection<AttributeValue<?>> values = (Collection<AttributeValue<?>>) attribute;
                    values.forEach(
                        value -> addValueToHeader(message, entry.getValue(), value.getValue()));
                } else {
                    LOGGER.warn("Can not add {} to headers. Expecting class Collection but got {}.", attribute, attribute.getClass());
                }
            });
    }

    private void addValueToHeader(MimeMessage message, String headerName, Object value) {
        try {
            if (value instanceof String) {
                message.addHeader(headerName, (String) value);
            } else {
                if (value != null) {
                    LOGGER.warn("Invalid type for value intended to be added as {} header. Expecting String but got {}", headerName, value.getClass());
                }
            }
        } catch (MessagingException e) {
            LOGGER.warn("Could not add header {} with value {}", headerName, value);
        }
    }

}
