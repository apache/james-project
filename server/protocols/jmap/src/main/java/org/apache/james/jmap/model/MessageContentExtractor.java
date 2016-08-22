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

package org.apache.james.jmap.model;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;

import com.github.fge.lambdas.Throwing;

public class MessageContentExtractor {
    
    public MessageContent extract(org.apache.james.mime4j.dom.Message message) throws IOException {
        Body body = message.getBody();
        if (body instanceof TextBody) {
            return parseTextBody(message, (TextBody)body);
        }
        if (body instanceof Multipart){
            return parseMultipart(message, (Multipart)body);
        }
        return MessageContent.empty();
    }

    private MessageContent parseTextBody(Entity entity, TextBody textBody) throws IOException {
        String bodyContent = asString(textBody);
        if ("text/html".equals(entity.getMimeType())) {
            return MessageContent.ofHtmlOnly(bodyContent);
        }
        return MessageContent.ofTextOnly(bodyContent);
    }

    private MessageContent parseMultipart(Entity entity, Multipart multipart) throws IOException {
        if ("multipart/alternative".equals(entity.getMimeType())) {
            return parseMultipartAlternative(multipart);
        }
        return parseMultipartMixed(multipart);
    }

    private String asString(TextBody textBody) throws IOException {
        return IOUtils.toString(textBody.getInputStream(), textBody.getMimeCharset());
    }

    private MessageContent parseMultipartMixed(Multipart multipart) throws IOException {
        List<Entity> parts = multipart.getBodyParts();
        if (! parts.isEmpty()) {
            Entity firstPart = parts.get(0);
            if (firstPart.getBody() instanceof Multipart && "multipart/alternative".equals(firstPart.getMimeType())) {
                return parseMultipartAlternative((Multipart)firstPart.getBody());
            } else {
                if (firstPart.getBody() instanceof TextBody) {
                    return parseTextBody(firstPart, (TextBody)firstPart.getBody());
                }
            }
        }
        return MessageContent.empty();
    }

    private MessageContent parseMultipartAlternative(Multipart multipart) throws IOException {
        Optional<String> textBody = getFirstMatchingTextBody(multipart, "text/plain");
        Optional<String> htmlBody = getFirstMatchingTextBody(multipart, "text/html");
        return new MessageContent(textBody, htmlBody);
    }

    private Optional<String> getFirstMatchingTextBody(Multipart multipart, String mimeType) throws IOException {
        return multipart.getBodyParts()
                .stream()
                .filter(part -> mimeType.equals(part.getMimeType()))
                .map(Entity::getBody)
                .filter(TextBody.class::isInstance)
                .map(TextBody.class::cast)
                .findFirst()
                .map(Throwing.function(this::asString).sneakyThrow());
    }

    public static class MessageContent {
        private final Optional<String> textBody;
        private final Optional<String> htmlBody;

        public MessageContent(Optional<String> textBody, Optional<String> htmlBody) {
            this.textBody = textBody;
            this.htmlBody = htmlBody;
        }

        public static MessageContent ofTextOnly(String textBody) {
            return new MessageContent(Optional.of(textBody), Optional.empty());
        }

        public static MessageContent ofHtmlOnly(String htmlBody) {
            return new MessageContent(Optional.empty(), Optional.of(htmlBody));
        }

        public static MessageContent empty() {
            return new MessageContent(Optional.empty(), Optional.empty());
        }
        
        public Optional<String> getTextBody() {
            return textBody;
        }

        public Optional<String> getHtmlBody() {
            return htmlBody;
        }
    }
}
