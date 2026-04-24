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

package org.apache.james.protocols.smtp.core;

import java.net.IDN;

/**
 * Address-string normalisation helpers shared by MAIL FROM and RCPT TO
 * handling. Address validity proper lives in
 * {@link org.apache.james.core.MailAddress}; this class is concerned only
 * with the protocol-layer transforms.
 */
final class AddressNormalization {

    private AddressNormalization() {
    }

    /**
     * Convert any {@code xn--} labels (IDNA A-labels) in the domain part of
     * {@code address} to their Unicode (U-label) form, leaving the local
     * part untouched. Addresses without an {@code @} or without an
     * {@code xn--} substring are returned unchanged.
     *
     * This runs regardless of whether the client declared SMTPUTF8, because
     * an A-label-only address is purely ASCII on the wire and has always
     * been valid SMTP. Storing the decoded U-label form lets upper layers
     * reason about one canonical address.
     *
     * @throws IllegalArgumentException if any label still starts with
     *         {@code xn--} after {@link IDN#toUnicode(String, int)} — which
     *         indicates a malformed A-label that the IDN decoder could not
     *         interpret.
     */
    static String aceLabelsToUnicode(String address) {
        int at = address.lastIndexOf('@');
        if (at < 0 || !address.substring(at + 1).contains("xn--")) {
            return address;
        }
        String localPart = address.substring(0, at);
        String domain = address.substring(at + 1);
        String unicodeDomain = IDN.toUnicode(domain, IDN.ALLOW_UNASSIGNED);
        if (unicodeDomain.startsWith("xn--") || unicodeDomain.contains(".xn--")) {
            throw new IllegalArgumentException(
                "Malformed A-label in domain: " + domain);
        }
        return localPart + "@" + unicodeDomain;
    }
}
