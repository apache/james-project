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

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.ParseException;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/**
 * The shapes of a S/MIME signed message (RFC 8551 section 3.5): a <code>multipart/signed</code> with a PKCS#7
 * signature protocol and exactly two parts, the content and its signature, or an
 * <code>application/(x-)pkcs7-mime</code> with <code>smime-type=signed-data</code>, or without smime-type when its CMS content is signed data.
 */
public class SMIMESignedMessages {
    private static final Logger LOGGER = LoggerFactory.getLogger(SMIMESignedMessages.class);

    private static final ImmutableSet<String> PKCS7_SIGNATURE_PROTOCOLS = ImmutableSet.of(
        "application/pkcs7-signature",
        "application/x-pkcs7-signature");
    private static final String SIGNED_DATA = "signed-data";

    public static boolean isSigned(MimeMessage message) throws MessagingException {
        return isPkcs7SignedMultipart(message) || isSignedData(message);
    }

    /**
     * A <code>multipart/signed</code> body is not necessarily S/MIME: PGP/MIME (RFC 3156) relies on the very
     * same content type and only the <code>protocol</code> parameter tells the two apart (RFC 8551 section 3.4.3).
     */
    private static boolean isPkcs7SignedMultipart(MimeMessage message) throws MessagingException {
        if (!message.isMimeType("multipart/signed")) {
            return false;
        }
        boolean pkcs7Protocol = parameter(message, "protocol")
            .map(protocol -> protocol.toLowerCase(Locale.US).trim())
            .filter(PKCS7_SIGNATURE_PROTOCOLS::contains)
            .isPresent();
        return pkcs7Protocol && hasTwoParts(message);
    }

    private static boolean hasTwoParts(MimeMessage message) {
        try {
            return message.getContent() instanceof MimeMultipart multipart && multipart.getCount() == 2;
        } catch (IOException | MessagingException e) {
            LOGGER.info("Could not read the parts of a multipart/signed message, treating it as not S/MIME signed", e);
            return false;
        }
    }

    private static boolean isSignedData(MimeMessage message) throws MessagingException {
        if (!message.isMimeType("application/pkcs7-mime") && !message.isMimeType("application/x-pkcs7-mime")) {
            return false;
        }
        try {
            String smimeType = new ContentType(message.getContentType()).getParameter("smime-type");
            if (smimeType == null) {
                // Some clients omit smime-type: the CMS content type then tells signed data from enveloped data
                return hasSignedDataContent(message);
            }
            return smimeType.trim().equalsIgnoreCase(SIGNED_DATA);
        } catch (ParseException e) {
            LOGGER.info("Could not parse the Content-Type of a message, treating it as not S/MIME signed", e);
            return false;
        }
    }

    private static boolean hasSignedDataContent(MimeMessage message) {
        try (ASN1InputStream asn1 = new ASN1InputStream(message.getInputStream())) {
            ContentInfo contentInfo = ContentInfo.getInstance(asn1.readObject());
            return contentInfo != null && CMSObjectIdentifiers.signedData.equals(contentInfo.getContentType());
        } catch (IOException | MessagingException | IllegalArgumentException e) {
            LOGGER.info("Could not read the CMS content of a message, treating it as not S/MIME signed", e);
            return false;
        }
    }

    private static Optional<String> parameter(MimeMessage message, String name) throws MessagingException {
        try {
            return Optional.ofNullable(new ContentType(message.getContentType()).getParameter(name));
        } catch (ParseException e) {
            LOGGER.info("Could not parse the Content-Type of a message, treating it as not S/MIME signed", e);
            return Optional.empty();
        }
    }
}
