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
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.transport.mailets.delivery.MailStore;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.consumers.ThrowingConsumer;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Booleans;

/**
 * WithStorageDirective position storage directive for the recipients of this email.
 *
 * These directives are used by <strong>LocalDelivery</strong> mailet when adding the email to the recipients mailboxes.
 *
 * The following storage directives can be set:
 *  - targetFolderName: the folder to append the email in. Defaults to none (INBOX).
 *  - seen: boolean, whether the message should be automatically marked as seen. Defaults to false.
 *  - important: boolean, whether the message should be automatically marked as important. Defaults to false.
 *  - keywords: set of string, encoded as a string (value are coma separated). IMAP user flags to set for the message. Defaults to none.
 *
 *  At least one of the storage directives should be set.
 *
 *  Example:
 *
 *  <mailet match="IsMarkedAsSpam" class="WithStorageDirective">
 *      <targetFolderName>Spam</targetFolderName>
 *      <seen>true</seen>
 *      <important>true</important>
 *      <keywords>keyword1,keyword2</targetFolderName>
 *  </mailet>
 */
public class WithStorageDirective extends GenericMailet {
    static final String TARGET_FOLDER_NAME = "targetFolderName";
    static final String SEEN = "seen";
    static final String IMPORTANT = "important";
    static final String KEYWORDS = "keywords";
    private static final Splitter KEYWORD_SPLITTER = Splitter.on(',')
        .omitEmptyStrings()
        .trimResults();

    private final UsersRepository usersRepository;

    private Optional<AttributeValue<String>> targetFolderName;
    private Optional<AttributeValue<Boolean>> seen;
    private Optional<AttributeValue<Boolean>> important;
    private Optional<AttributeValue<Collection<AttributeValue<?>>>> keywords;

    @Inject
    public WithStorageDirective(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Override
    public void init() throws MessagingException {
        Preconditions.checkState(
            Booleans.countTrue(
                getInitParameterAsOptional(TARGET_FOLDER_NAME).isPresent(),
                getInitParameterAsOptional(SEEN).isPresent(),
                getInitParameterAsOptional(IMPORTANT).isPresent(),
                getInitParameterAsOptional(KEYWORDS).isPresent()) > 0,
                "Expecting one of the storage directives to be specified: [%s, %s, %s, %s]",
                TARGET_FOLDER_NAME, SEEN, IMPORTANT, KEYWORDS);
        Preconditions.checkState(validBooleanParameter(getInitParameterAsOptional(SEEN)), "'%s' needs to be a boolean", SEEN);
        Preconditions.checkState(validBooleanParameter(getInitParameterAsOptional(IMPORTANT)), "'%s' needs to be a boolean", IMPORTANT);

        targetFolderName = getInitParameterAsOptional(TARGET_FOLDER_NAME).map(AttributeValue::of);
        seen = getInitParameterAsOptional(SEEN).map(Boolean::parseBoolean).map(AttributeValue::of);
        important = getInitParameterAsOptional(IMPORTANT).map(Boolean::parseBoolean).map(AttributeValue::of);
        keywords = getInitParameterAsOptional(KEYWORDS).map(this::parseKeywords).map(AttributeValue::of);
    }

    private Collection<AttributeValue<?>> parseKeywords(String s) {
        return KEYWORD_SPLITTER
            .splitToStream(s)
            .map(AttributeValue::of)
            .collect(ImmutableSet.toImmutableSet());
    }

    private boolean validBooleanParameter(Optional<String> parameter) {
        return parameter.map(v -> v.equals("true") || v.equals("false"))
            .orElse(true);
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        mail.getRecipients()
            .forEach(addStorageDirective(mail));
    }

    public ThrowingConsumer<MailAddress> addStorageDirective(Mail mail) {
        return recipient -> {
            Username username = usersRepository.getUsername(recipient);
            targetFolderName.ifPresent(value -> mail.setAttribute(new Attribute(AttributeName.of(MailStore.DELIVERY_PATH_PREFIX + username.asString()), value)));
            seen.ifPresent(value -> mail.setAttribute(new Attribute(AttributeName.of(MailStore.SEEN_PREFIX + username.asString()), value)));
            important.ifPresent(value -> mail.setAttribute(new Attribute(AttributeName.of(MailStore.IMPORTANT_PREFIX + username.asString()), value)));
            keywords.ifPresent(value -> mail.setAttribute(new Attribute(AttributeName.of(MailStore.KEYWORDS_PREFIX + username.asString()), value)));
        };
    }
}
