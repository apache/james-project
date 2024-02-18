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
import java.util.Locale;
import java.util.Optional;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.collect.ImmutableList;

/**
 * <p>
 * Matches mails having a <pre>org.apache.james.spamassassin.status</pre> per recipient header with a <pre>Yes</pre> value.
 * </p>
 * 
 * As an example, here is a part of a mailet pipeline which can be used in your LocalDelivery processor:
 * <pre>{@code
 * <!-- SpamAssassing mailets pipeline -->
 *     <mailet match="RecipientIsLocal" class="SpamAssassin">
 *         <spamdHost>spamassassin</spamdHost>
 *         <spamdPort>783</spamdPort>
 *     </mailet>
 *     <mailet match="IsMarkedAsSpam" class="WithStorageDirective">
 *         <targetFolderName>Spam</targetFolderName>
 *     </mailet>
 * <!-- End of SpamAssassing mailets pipeline -->
 * }</pre>
 */
public class IsMarkedAsSpam extends GenericMatcher {

    private static final String YES = "yes";

    public static final String STATUS_MAIL_PACKAGE_DEFAULT = "org.apache.james.spamassassin.status";

    private String spamStatusHeader = STATUS_MAIL_PACKAGE_DEFAULT;

    @Override
    public String getMatcherInfo() {
        return "Has " + spamStatusHeader + " per recipient header with a Yes value";
    }

    @Override
    public void init() throws MessagingException {
        spamStatusHeader = Optional.ofNullable(getCondition())
            .filter(s -> !s.isEmpty())
            .orElse(STATUS_MAIL_PACKAGE_DEFAULT);
    }

    @Override
    public Collection<MailAddress> match(Mail mail) {
        return mail.getRecipients()
            .stream()
            .filter(recipient -> isMarkedAsSpam(mail, recipient))
            .collect(ImmutableList.toImmutableList());
    }

    public boolean isMarkedAsSpam(Mail mail, MailAddress recipient) {
        return mail.getPerRecipientSpecificHeaders().getHeadersForRecipient(recipient)
            .stream()
            .filter(header -> header.getName().equals(spamStatusHeader))
            .anyMatch(header -> header.getValue().toLowerCase(Locale.US).startsWith(YES));
    }

}
