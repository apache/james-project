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

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.mailet.MailAddress;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

public class MailAddressUtils {

    public static List<MailAddress> from(InternetAddress[] internetAddresses) throws AddressException {
        return from(ImmutableList.copyOf(internetAddresses));
    }

    private static List<MailAddress> from(List<InternetAddress> internetAddresses) throws AddressException {
        ImmutableList.Builder<MailAddress> builder = ImmutableList.builder();
        for (InternetAddress internetAddress : internetAddresses) {
            builder.add(new MailAddress(internetAddress));
        }
        return builder.build();
    }

    public static List<InternetAddress> toInternetAddresses(List<MailAddress> mailAddresses) {
        return iterableOfInternetAddress(mailAddresses)
            .toList();
    }

    public static InternetAddress[] toInternetAddressArray(List<MailAddress> mailAddresses) {
        return iterableOfInternetAddress(mailAddresses)
            .toArray(InternetAddress.class);
    }

    private static FluentIterable<InternetAddress> iterableOfInternetAddress(List<MailAddress> mailAddresses) {
        return FluentIterable.from(mailAddresses)
            .transform(MailAddress::toInternetAddress);
    }

    public static boolean isUnalteredOrReversePathOrSender(MailAddress mailAddress) {
        return mailAddress.equals(SpecialAddress.UNALTERED) 
                || mailAddress.equals(SpecialAddress.REVERSE_PATH) 
                || mailAddress.equals(SpecialAddress.SENDER);
    }
}
