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
import jakarta.mail.internet.InternetAddress;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.mailets.redirect.RedirectNotify;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.mailet.Mail;

import com.google.common.collect.ImmutableList;

public class TosUtils {

    public static TosUtils from(RedirectNotify mailet) {
        return new TosUtils(mailet);
    }

    private final RedirectNotify mailet;

    private TosUtils(RedirectNotify mailet) {
        this.mailet = mailet;
    }

    public List<MailAddress> getTo(Mail originalMail) throws MessagingException {
        List<InternetAddress> apparentlyTo = mailet.getTo();
        if (!apparentlyTo.isEmpty()) {
            if (containsOnlyUnalteredOrTo(apparentlyTo)) {
                return ImmutableList.of();
            }
            return SpecialAddressesUtils.from(mailet).replaceInternetAddresses(originalMail, apparentlyTo);
        }

        return ImmutableList.of();
    }

    private boolean containsOnlyUnalteredOrTo(List<InternetAddress> apparentlyTo) {
        return apparentlyTo.size() == 1 && 
                (apparentlyTo.get(0).equals(SpecialAddress.UNALTERED.toInternetAddress().get()) || apparentlyTo.get(0).equals(SpecialAddress.RECIPIENTS.toInternetAddress().get()));
    }
}
