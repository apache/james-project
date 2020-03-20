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

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.mailets.delivery.MailStore;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.consumers.ThrowingConsumer;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * WithStorageDirective position storage directive for the recipients of this email.
 *
 * These directives are used by <strong>LocalDelivery</strong> mailet when adding the email to the recipients mailboxes.
 *
 * The following storage directives can be set:
 *  - targetFolderName: the folder to append the email in. (compulsory)
 *
 *  Example:
 *
 *  <mailet match="IsMarkedAsSpam" class="WithStorageDirective">
 *      <targetFolderName>Spam</targetFolderName>
 *  </mailet>
 */
public class WithStorageDirective extends GenericMailet {

    public static final String TARGET_FOLDER_NAME = "targetFolderName";

    private final UsersRepository usersRepository;

    private AttributeValue<String> targetFolderName;

    @Inject
    public WithStorageDirective(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Override
    public void init() throws MessagingException {
        targetFolderName = AttributeValue.of(validateMailetConfiguration(TARGET_FOLDER_NAME));
    }

    private String validateMailetConfiguration(String initParameterName) {
        String initParameterValue = getInitParameter(initParameterName);
        Preconditions.checkState(!Strings.isNullOrEmpty(initParameterValue), "You need to specify %s", initParameterName);
        return initParameterValue;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        mail.getRecipients()
            .forEach(addStorageDirective(mail));
    }

    public ThrowingConsumer<MailAddress> addStorageDirective(Mail mail) {
        return recipient -> {
            AttributeName attributeNameForUser = AttributeName.of(MailStore.DELIVERY_PATH_PREFIX + usersRepository.getUsername(recipient).asString());
            mail.setAttribute(new Attribute(attributeNameForUser, targetFolderName));
        };

    }
}
