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

import static org.apache.mailet.base.RFC2822Headers.CONTENT_TYPE;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;

import org.apache.james.core.MailAddress;
import org.apache.james.javax.MultipartUtil;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.util.StreamUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;


/**
 * <p>This matcher checks if the content type matches.</p>
 *
 * <p>This matcher walks down the mime tree and will try to match any of the mime parts of this message.</p>
 *
 * use: <pre><code><mailet match="HasMimeTypeAnySubPart=text/plain,text/html" class="..." /></code></pre>
 */
public class HasMimeTypeAnySubPart extends GenericMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(HasMimeTypeAnySubPart.class);
    private static final String MULTIPART_MIME_TYPE = "multipart/*";

    private Set<String> acceptedContentTypes;

    @Override
    public void init() throws MessagingException {
        acceptedContentTypes = ImmutableSet.copyOf(Splitter.on(",").trimResults().split(getCondition()));
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        return detectMatchingMimeTypes(mail.getMessage())
            .findAny()
            .map(any -> mail.getRecipients())
            .orElse(ImmutableList.of());
    }


    private boolean isMultipart(Part part) throws MailetException {
        try {
            return part.isMimeType(MULTIPART_MIME_TYPE);
        } catch (MessagingException e) {
            throw new MailetException("Could not retrieve contenttype of MimePart.", e);
        }
    }

    private Stream<String> detectMatchingMimeTypes(Part part) throws MailetException {
        try {
            if (isMultipart(part)) {
                Multipart multipart = (Multipart) part.getContent();

                return Stream.concat(
                    MultipartUtil.retrieveBodyParts(multipart)
                        .stream()
                        .flatMap(Throwing.function(this::detectMatchingMimeTypes).sneakyThrow()),
                    detectMatchingMimeTypesNoRecursion(part));
            }

            return detectMatchingMimeTypesNoRecursion(part);
        } catch (MessagingException | IOException e) {
            throw new MailetException("Could not retrieve contenttype of MimePart.", e);
        }
    }

    private Stream<String> detectMatchingMimeTypesNoRecursion(Part part) throws MessagingException {
        return StreamUtils.ofNullable(part.getHeader(CONTENT_TYPE))
            .flatMap(this::getMimeType)
            .filter(acceptedContentTypes::contains);
    }

    private Stream<String> getMimeType(String rawValue) {
        try {
            return Stream.of(Fields.contentType(rawValue).getMimeType());
        } catch (Exception e) {
            LOGGER.warn("Error while parsing message's mimeType {}", rawValue, e);
            return Stream.empty();
        }
    }

}
