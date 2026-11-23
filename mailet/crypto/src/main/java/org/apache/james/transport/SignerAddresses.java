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

package org.apache.james.transport;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mail addresses a signer certificate is issued for (RFC 8550 section 3): the rfc822Name entries of its
 * subjectAltName, or, without any, the EMAILADDRESS of its subject.
 */
public class SignerAddresses {
    private static final Logger LOGGER = LoggerFactory.getLogger(SignerAddresses.class);
    private static final int RFC822_NAME = 1;

    public static List<MailAddress> of(X509Certificate certificate) {
        List<MailAddress> subjectAltNames = rfc822Names(certificate).stream()
            .flatMap(name -> parse(name).stream())
            .toList();
        if (!subjectAltNames.isEmpty()) {
            return subjectAltNames;
        }
        return Arrays.stream(X500Name.getInstance(certificate.getSubjectX500Principal().getEncoded()).getRDNs(BCStyle.EmailAddress))
            .map(rdn -> IETFUtils.valueToString(rdn.getFirst().getValue()))
            .flatMap(name -> parse(name).stream())
            .toList();
    }

    private static List<String> rfc822Names(X509Certificate certificate) {
        try {
            Collection<List<?>> names = Optional.ofNullable(certificate.getSubjectAlternativeNames()).orElse(List.of());
            return names.stream()
                .filter(name -> name.size() >= 2 && Integer.valueOf(RFC822_NAME).equals(name.get(0)) && name.get(1) instanceof String)
                .map(name -> (String) name.get(1))
                .toList();
        } catch (CertificateParsingException e) {
            LOGGER.info("Could not read the subject alternative names of {}", certificate.getSubjectX500Principal(), e);
            return List.of();
        }
    }

    public static Optional<MailAddress> parse(String address) {
        try {
            return Optional.of(new MailAddress(address));
        } catch (AddressException e) {
            return Optional.empty();
        }
    }

    /**
     * RFC 8550 section 3: the domain is compared ignoring its case, and so is the local part.
     */
    public static boolean sameAddress(MailAddress signerAddress, MailAddress sender) {
        return signerAddress.getLocalPart().equalsIgnoreCase(sender.getLocalPart())
            && signerAddress.getDomain().equals(sender.getDomain());
    }
}
