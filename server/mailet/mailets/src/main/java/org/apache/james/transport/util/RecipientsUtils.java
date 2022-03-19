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
package org.apache.james.transport.util;

import java.util.List;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.mailets.redirect.RedirectNotify;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.mailet.Mail;

import com.google.common.collect.ImmutableList;

public class RecipientsUtils {

    public static RecipientsUtils from(RedirectNotify mailet) {
        return new RecipientsUtils(mailet);
    }

    private final RedirectNotify mailet;

    private RecipientsUtils(RedirectNotify mailet) {
        this.mailet = mailet;
    }

    public List<MailAddress> getRecipients(Mail originalMail) throws MessagingException {
        List<MailAddress> recipients = mailet.getRecipients();
        if (!recipients.isEmpty()) {
            if (containsOnlyUnalteredOrRecipients(recipients)) {
                return ImmutableList.of();
            }
            return SpecialAddressesUtils.from(mailet).replaceSpecialAddresses(originalMail, recipients);
        }
        return ImmutableList.of();
    }

    private boolean containsOnlyUnalteredOrRecipients(List<MailAddress> recipients) {
        return recipients.size() == 1 && 
                (recipients.contains(SpecialAddress.UNALTERED) || recipients.contains(SpecialAddress.RECIPIENTS));
    }
}
