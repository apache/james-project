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


package org.apache.james.transport.matchers;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;


/**
 * <p>This matcher checks if the content type matches.</p>
 *
 * use: <pre><code><mailet match="HasMimeType=text/plain,text/html" class="..." /></code></pre>
 */
public class HasMimeType extends GenericMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(HasMimeType.class);

    private Set<String> acceptedContentTypes;

    @Override
    public void init() throws javax.mail.MessagingException {
        acceptedContentTypes = ImmutableSet.copyOf(Splitter.on(",").trimResults().split(getCondition()));
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws javax.mail.MessagingException {
        Optional<String> mimeTypes = getMimeTypeFromMessage(mail.getMessage());

        return mimeTypes.filter(acceptedContentTypes::contains)
                .map(any -> mail.getRecipients())
                .orElse(ImmutableList.of());
    }

    private static Optional<String> getMimeTypeFromMessage(MimeMessage message) throws MessagingException {
        try {
            return Optional.of(new MimeType(message.getContentType()).getBaseType());
        } catch (MimeTypeParseException e) {
            LOGGER.warn("Error while parsing message's mimeType {}", message.getContentType(), e);
            return Optional.empty();
        }
    }

}

