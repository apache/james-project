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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.ParseException;

import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Strings;

public class ContentReplacer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContentReplacer.class);

    private final boolean debug;

    public ContentReplacer(boolean debug) {
        this.debug = debug;
    }

    public String applyPatterns(List<ReplacingPattern> patterns, String text) {
        String textCopy = new String(text);
        for (ReplacingPattern replacingPattern : patterns) {
            textCopy = applyPattern(replacingPattern, textCopy);
        }
        return textCopy;
    }

    private String applyPattern(ReplacingPattern replacingPattern, String text) {
        boolean changed;
        int index = 0;
        do {
            changed = false;
            Matcher matcher = replacingPattern.getMatcher().matcher(text);
            if (matcher.find(index)) {
                text = replaceFirst(replacingPattern, matcher);
                changed = true;
                index++;
            }
        } while (shouldRepeat(replacingPattern, changed));
        return text;
    }

    private String replaceFirst(ReplacingPattern replacingPattern, Matcher matcher) {
        if (debug) {
            LOGGER.debug("Subject rule match: {}", replacingPattern.getMatcher());
        }
        return matcher.replaceFirst(replacingPattern.getSubstitution());
    }

    private boolean shouldRepeat(ReplacingPattern replacingPattern, boolean changed) {
        return replacingPattern.isRepeat() && changed;
    }

    public void replaceMailContentAndSubject(Mail mail, ReplaceConfig replaceConfig, Optional<Charset> charset) throws MailetException {
        try {
            boolean subjectChanged = applySubjectReplacingUnits(mail, replaceConfig, charset);
            boolean contentChanged = applyBodyReplacingUnits(mail, replaceConfig, charset);

            if (subjectChanged || contentChanged) {
                mail.getMessage().saveChanges();
            }
        } catch (MessagingException | IOException e) {
            throw new MailetException("Error in replace", e);
        }
    }

    private boolean applySubjectReplacingUnits(Mail mail, ReplaceConfig replaceConfig, Optional<Charset> maybeCharset) throws MessagingException {
        if (!replaceConfig.getSubjectReplacingUnits().isEmpty()) {
            String subject = applyPatterns(replaceConfig.getSubjectReplacingUnits(), 
                    Strings.nullToEmpty(mail.getMessage().getSubject()));
            String charset = maybeCharset.map(Charset::name)
                .orElseGet(Throwing.supplier(() -> previousCharset(mail)).sneakyThrow());
            mail.getMessage().setSubject(subject, charset);
            return true;
        }
        return false;
    }

    private String previousCharset(Mail mail) throws MessagingException {
        ContentType contentType = new ContentType(mail.getMessage().getContentType());
        return contentType.getParameter("Charset");
    }

    private boolean applyBodyReplacingUnits(Mail mail, ReplaceConfig replaceConfig, Optional<Charset> charset) throws IOException, MessagingException, ParseException {
        if (!replaceConfig.getBodyReplacingUnits().isEmpty()) {
            Object bodyObj = mail.getMessage().getContent();
            if (bodyObj instanceof String) {
                String body = applyPatterns(replaceConfig.getBodyReplacingUnits(), 
                        Strings.nullToEmpty((String) bodyObj));
                setContent(mail, body, charset);
                return true;
            }
        }
        return false;
    }

    private void setContent(Mail mail, String body, Optional<Charset> charset) throws MessagingException, ParseException {
        mail.getMessage().setContent(body, getContentType(mail, charset));
    }

    private String getContentType(Mail mail, Optional<Charset> charset) throws MessagingException, ParseException {
        String contentTypeAsString = mail.getMessage().getContentType();
        if (charset.isPresent()) {
            ContentType contentType = new ContentType(contentTypeAsString);
            contentType.setParameter("charset", charset.get().name());
            return contentType.toString();
        }
        return contentTypeAsString;
    }

}
