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


package org.apache.james.transport.matcher;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.ParseException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/**
 * checks if a mail is smime signed. 

 */
public class IsSMIMESigned extends GenericMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(IsSMIMESigned.class);

    private static final ImmutableSet<String> PKCS7_SIGNATURE_PROTOCOLS = ImmutableSet.of(
        "application/pkcs7-signature",
        "application/x-pkcs7-signature");

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        if (mail == null) {
            return null;
        }
        
        MimeMessage message = mail.getMessage();
        if (message == null) {
            return null;
        }

        if (isPkcs7SignedMultipart(message)
                || message.isMimeType("application/pkcs7-signature")
                || message.isMimeType("application/x-pkcs7-signature")
                || ((message.isMimeType("application/pkcs7-mime") || message.isMimeType("application/x-pkcs7-mime")) 
                        && message.getContentType().contains("signed-data"))) {
            return mail.getRecipients();
        } else {
            return null;
        }
    }

    /**
     * A <code>multipart/signed</code> body is not necessarily S/MIME: PGP/MIME (RFC 3156) relies on the very
     * same content type and only the <code>protocol</code> parameter tells the two apart (RFC 8551 section 3.4.3).
     * Handing a PGP signature over to the S/MIME parser makes it choke on ASN.1 it can not read, hence we require
     * the protocol to explicitly designate a PKCS#7 signature.
     */
    private boolean isPkcs7SignedMultipart(MimeMessage message) throws MessagingException {
        if (!message.isMimeType("multipart/signed")) {
            return false;
        }
        return protocolParameter(message)
            .map(protocol -> protocol.toLowerCase(Locale.US).trim())
            .filter(PKCS7_SIGNATURE_PROTOCOLS::contains)
            .isPresent();
    }

    private Optional<String> protocolParameter(MimeMessage message) throws MessagingException {
        try {
            return Optional.ofNullable(new ContentType(message.getContentType()).getParameter("protocol"));
        } catch (ParseException e) {
            LOGGER.info("Could not parse Content-Type of a multipart/signed message, treating it as not S/MIME signed", e);
            return Optional.empty();
        }
    }
}
