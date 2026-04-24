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

package org.apache.james.transport.mailets.remote.delivery;

import java.net.IDN;
import java.util.Collection;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import org.apache.james.core.MaybeSender;

/**
 * Picks a relaying strategy for the RFC 6531 SMTPUTF8 extension based on
 * what the remote MX advertises and what's in the envelope. We assume all
 * MXes for a single destination domain advertise the same extensions; if
 * the first MX we try lacks SMTPUTF8 we don't retry subsequent ones hoping
 * they'll differ.
 */
public final class SmtpUtf8Strategy {

    public enum Action {
        /** No envelope address has non-ASCII characters; deliver as-is. */
        NO_UTF8_NEEDED,
        /** Some envelope address has non-ASCII characters and the remote
         *  advertises SMTPUTF8; deliver as-is and assert SMTPUTF8. */
        USE_EXTENSION,
        /** Remote lacks SMTPUTF8 but all non-ASCII lives in the domain
         *  part, which can be downgraded to ACE (A-label, xn--) form. */
        DOWNGRADE_DOMAINS,
        /** Remote lacks SMTPUTF8 and at least one local part is non-ASCII,
         *  so no lossless downgrade exists. Caller should fail the
         *  transaction the same way it fails a SIZE overflow. */
        CANNOT_DOWNGRADE
    }

    private SmtpUtf8Strategy() {
    }

    public static Action pick(MaybeSender sender,
                              Collection<InternetAddress> recipients,
                              boolean remoteSupportsSmtpUtf8) {
        boolean nonAsciiLocalPart = hasNonAsciiLocalPart(sender, recipients);
        boolean nonAsciiDomain = hasNonAsciiDomain(sender, recipients);

        if (!nonAsciiLocalPart && !nonAsciiDomain) {
            return Action.NO_UTF8_NEEDED;
        }
        if (remoteSupportsSmtpUtf8) {
            return Action.USE_EXTENSION;
        }
        if (nonAsciiLocalPart) {
            return Action.CANNOT_DOWNGRADE;
        }
        return Action.DOWNGRADE_DOMAINS;
    }

    /**
     * Returns a copy of {@code address} with its domain converted to ACE
     * (A-label) form via {@link IDN#toASCII}. Passing an already-ASCII
     * domain through this is a no-op, so callers don't need to check.
     *
     * @throws AddressException if the address has no {@code @}
     */
    public static InternetAddress toAceDomain(InternetAddress address) throws AddressException {
        String asString = address.getAddress();
        int at = asString.lastIndexOf('@');
        if (at < 0) {
            throw new AddressException("Address has no @: " + asString);
        }
        String localPart = asString.substring(0, at);
        String domain = asString.substring(at + 1);
        // InternetAddress(String) parses strictly; bypass via setAddress so
        // we don't reject local parts we're only passing through unchanged.
        InternetAddress result = new InternetAddress();
        result.setAddress(localPart + "@" + IDN.toASCII(domain, IDN.ALLOW_UNASSIGNED));
        return result;
    }

    /** ACE form of the string address. See {@link #toAceDomain}. */
    public static String aceAddressString(String address) {
        int at = address.lastIndexOf('@');
        if (at < 0) {
            return address;
        }
        return address.substring(0, at + 1)
            + IDN.toASCII(address.substring(at + 1), IDN.ALLOW_UNASSIGNED);
    }

    private static boolean hasNonAsciiLocalPart(MaybeSender sender,
                                                Collection<InternetAddress> recipients) {
        if (!sender.isNullSender()
                && containsNonAscii(sender.asString().substring(0, Math.max(0, sender.asString().lastIndexOf('@'))))) {
            return true;
        }
        for (InternetAddress a : recipients) {
            int at = a.getAddress().lastIndexOf('@');
            String localPart = at < 0 ? a.getAddress() : a.getAddress().substring(0, at);
            if (containsNonAscii(localPart)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonAsciiDomain(MaybeSender sender,
                                             Collection<InternetAddress> recipients) {
        if (!sender.isNullSender()) {
            int at = sender.asString().lastIndexOf('@');
            if (at >= 0 && containsNonAscii(sender.asString().substring(at + 1))) {
                return true;
            }
        }
        for (InternetAddress a : recipients) {
            int at = a.getAddress().lastIndexOf('@');
            if (at >= 0 && containsNonAscii(a.getAddress().substring(at + 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNonAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7F) {
                return true;
            }
        }
        return false;
    }
}
