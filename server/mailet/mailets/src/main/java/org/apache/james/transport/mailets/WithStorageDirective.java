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
import org.apache.james.transport.mailets.delivery.StorageDirective;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.consumers.ThrowingConsumer;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

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

    private StorageDirective storageDirective;

    @Inject
    public WithStorageDirective(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Override
    public void init() throws MessagingException {
        Preconditions.checkState(validBooleanParameter(getInitParameterAsOptional(SEEN)), "'%s' needs to be a boolean", SEEN);
        Preconditions.checkState(validBooleanParameter(getInitParameterAsOptional(IMPORTANT)), "'%s' needs to be a boolean", IMPORTANT);

        storageDirective = StorageDirective.builder()
            .targetFolder(getInitParameterAsOptional(TARGET_FOLDER_NAME))
            .seen(getInitParameterAsOptional(SEEN).map(Boolean::parseBoolean))
            .important(getInitParameterAsOptional(IMPORTANT).map(Boolean::parseBoolean))
            .keywords(getInitParameterAsOptional(KEYWORDS).map(this::parseKeywords))
            .build();
    }

    private Collection<String> parseKeywords(String s) {
        return KEYWORD_SPLITTER
            .splitToList(s);
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
        return recipient -> storageDirective.encodeAsAttributes(usersRepository.getUsername(recipient))
            .forEach(mail::setAttribute);
    }
}
