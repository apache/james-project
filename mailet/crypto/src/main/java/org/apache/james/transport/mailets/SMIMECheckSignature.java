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

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.james.core.MailAddress;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.stream.RawFieldParser;
import org.apache.james.mime4j.util.ContentUtil;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.james.server.core.SubmittedHeaderFields;
import org.apache.james.transport.KeyStoreHolder;
import org.apache.james.transport.KeyStoreHolderConfiguration;
import org.apache.james.transport.KeyStoreHolderFactory;
import org.apache.james.transport.SMIMESignedMessages;
import org.apache.james.transport.SMIMESignerInfo;
import org.apache.james.transport.SignerAddresses;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.base.GenericMailet;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.mail.smime.SMIMEException;
import org.bouncycastle.mail.smime.SMIMESigned;
import org.eclipse.angus.mail.util.BASE64DecoderStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/**
 * <p>
 * Verifies the s/mime signature of a message. The s/mime signing ensure that
 * the private key owner is the real sender of the message. To be checked by
 * this mailet the s/mime signature must contain the actual signature, the
 * signer's certificate and optionally a set of certificate that can be used to
 * create a chain of trust that starts from the signer's certificate and leads
 * to a known trusted certificate.
 * </p>
 * <p>
 * This check is composed by two steps: firstly it's ensured that the signature
 * is valid, then it's checked if a chain of trust starting from the signer
 * certificate and that leads to a trusted certificate can be created. The first
 * check verifies that the the message has not been modified after the signature
 * was put and that the signer's certificate was valid at the time of the
 * signing. The latter should ensure that the signer is who he declare to be.
 * </p>
 * <p>
 * The results of the checks perfomed by this mailet are wrote as a mail
 * attribute which default name is org.apache.james.SMIMECheckSignature (it can
 * be changed using the mailet parameter <code>mailAttribute</code>). After
 * the check this attribute will contain a list of SMIMESignerInfo object, one
 * for each message's signer. These objects contain the signer's certificate and
 * the trust path.
 * </p>
 * <p>
 * A trusted signer is accepted only when its certificate names the sender: one of the rfc822Name of its
 * subjectAltName, or the EMAILADDRESS of its subject when it has none, matches the <code>From</code> or the
 * <code>Sender</code> of the message (RFC 8550 section 3), unless it names no e-mail address. The address is compared
 * ignoring its case. A message with several From or Sender fields, or whose From or Sender is read differently by
 * Jakarta Mail and mime4j, names no sender. Otherwise the status is <code>Signer mismatch</code> and no signer is recorded. The
 * certificate must allow e-mail protection when it restricts its extended key usage, and digitalSignature
 * or nonRepudiation when it restricts its key usage.
 * </p>
 * <p>
 * Optionally, specifying the parameter <code>strip</code>, the signature of
 * the message can be stripped after the check. The message will become a
 * standard message without an attached s/mime signature.
 * </p>
 * <p>
 * The configuration parameter of this mailet are summerized below. The firsts
 * defines the location, the format and the password of the keystore containing
 * the certificates that are considered trusted. Note: only the trusted certificate
 * entries are read, the key ones are not.
 * <ul>
 * <li>keyStoreType (default: jks): Certificate store format . "jks" is the
 * standard java certificate store format, but pkcs12 is also quite common and
 * compatible with standard email clients like Outlook Express and Thunderbird.
 * <li>keyStoreFileName (default: JAVA_HOME/jre/lib/security/cacert): Certificate
 * store path. The default store of the JDK holds the authorities of the web PKI, which issue server
 * certificates: configure a store of the authorities you trust for mail signing instead.
 * <li>keyStorePassword (default: ""): Certificate store password.
 * </ul>
 * Other parameters configure the behavior of the mailet:
 * <ul>
 * <li>strip (default: false): Defines if the s/mime signature of the message
 * have to be stripped after the check or not. Possible values are true and
 * false.
 * <li>mailAttribute (default: org.apache.james.SMIMECheckSignature):
 * specifies in which attribute the check results will be written.
 * <li>onlyTrusted (default: true): Usually a message signature to be
 * considered by this mailet as authentic must be valid and trusted. Setting
 * this mailet parameter to "false" the last condition is relaxed and also
 * "untrusted" signature are considered will be considered as authentic.
 * <li>maxSignerInfos (default: 10) and maxCertificates (default: 50): a message
 * carrying more signer infos or certificates is not checked, and gets a bad
 * signature status. Each signer info can lead to a certification path search
 * over all the certificates of the message: these limits bound the work a
 * remote sender can make this mailet do. The work of a single certification path
 * search can be bounded too, with the
 * <code>org.bouncycastle.x509.max_cert_path_build_nodes</code> system property.
 * </ul>
 * </p>
 * 
 */
public class SMIMECheckSignature extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(SMIMECheckSignature.class);

    private static final String SMIME_STATUS_HEADER = "X-SMIME-Status";

    private final FileSystem fileSystem;
    private KeyStoreHolder trustedCertificateStore;
    private boolean stripSignature = false;
    private boolean onlyTrusted = true;
    private AttributeName mailAttribute = AttributeName.of("org.apache.james.SMIMECheckSignature");
    private KeyStoreHolder.VerificationLimits verificationLimits = KeyStoreHolder.VerificationLimits.DEFAULT;

    @Inject
    public SMIMECheckSignature(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public void init() throws MessagingException {
        MailetConfig config = getMailetConfig();

        String stripSignatureConf = config.getInitParameter("strip");
        if (stripSignatureConf != null) {
            stripSignature = Boolean.parseBoolean(stripSignatureConf);
        }
        
        String onlyTrustedConf = config.getInitParameter("onlyTrusted");
        if (onlyTrustedConf != null) {
            onlyTrusted = Boolean.parseBoolean(onlyTrustedConf);
        }
        
        String mailAttributeConf = config.getInitParameter("mailAttribute");
        if (mailAttributeConf != null) {
            mailAttribute = AttributeName.of(mailAttributeConf);
        }

        try {
            verificationLimits = new KeyStoreHolder.VerificationLimits(
                Optional.ofNullable(config.getInitParameter("maxSignerInfos")).map(String::trim).map(Integer::parseInt)
                    .orElse(KeyStoreHolder.VerificationLimits.DEFAULT.maxSignerInfos()),
                Optional.ofNullable(config.getInitParameter("maxCertificates")).map(String::trim).map(Integer::parseInt)
                    .orElse(KeyStoreHolder.VerificationLimits.DEFAULT.maxCertificates()));
        } catch (IllegalArgumentException e) {
            throw new MessagingException("Invalid maxSignerInfos or maxCertificates", e);
        }

        trustedCertificateStore = KeyStoreHolderFactory.from(fileSystem).createKeyStoreHolder(KeyStoreHolderConfiguration.from(config));
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        // I extract the MimeMessage from the mail object and I check if the
        // mime type of the mail is one of the mime types that can contain a
        // signature.
        MimeMessage message = mail.getMessage();

        // strippedMessage will contain the signed content of the message 
        MimeBodyPart strippedMessage = null;
        
        List<SMIMESignerInfo> signers = null;

        boolean isMessageSigned = false;
        boolean isSignatureGood = false;
        boolean isSignerMismatch = false;
        
        try {
            SMIMESigned signed = asSMIMESigned(message);

            if (signed != null) {
                signers = trustedCertificateStore.verifySignatures(signed, verificationLimits);
                strippedMessage = signed.getContent();
                isMessageSigned = true;
            } else {
                LOGGER.info("Content not identified as signed");
            }
            
            // These errors are logged but they don't cause the message to change its state. The message
            // is considered as not signed and the process will go on.
        } catch (CMSException | SMIMEException e) {
            // Signatures are remote sender controlled thus we do not consider a malformed one to be a server error.
            LOGGER.warn("Error during the analysis of the signed message", e);
            signers = null;
        } catch (IOException e) {
            LOGGER.warn("IO error during the analysis of the signed message", e);
            signers = null;
        } catch (Exception e) {
            LOGGER.warn("Generic error occurred during the analysis of the message", e);
            signers = null;
        }
        
        // If at least one mail signer is found 
        // the mail attributes are set.
        if (signers != null) {
            List<SMIMESignerInfo> acceptedSigners = acceptedSigners(signers);
            List<SMIMESignerInfo> senderSigners = senderSigners(acceptedSigners, message);
            ArrayList<AttributeValue<?>> signerinfolist = signerInfoList(senderSigners);

            if (!signerinfolist.isEmpty()) {
                mail.setAttribute(new Attribute(mailAttribute, AttributeValue.of(signerinfolist)));
                isSignatureGood = true;
            } else {
                isSignerMismatch = !acceptedSigners.isEmpty();
                // if no valid signers are found the message is not modified.
                strippedMessage = null;
            }
        }

        setSMIMEStatus(mail, isMessageSigned, isSignatureGood, isSignerMismatch);

        if (stripSignature && strippedMessage != null) {
            stripSignature(mail, message, strippedMessage);
        }
    }

    private void setSMIMEStatus(Mail mail, boolean isMessageSigned, boolean isSignatureGood, boolean isSignerMismatch) throws MessagingException {
        if (!isMessageSigned) {
            mail.getMessage().setHeader(SMIME_STATUS_HEADER, "Not signed");
        } else if (isSignatureGood) {
            mail.getMessage().setHeader(SMIME_STATUS_HEADER, "Good signature");
        } else if (isSignerMismatch) {
            mail.getMessage().setHeader(SMIME_STATUS_HEADER, "Signer mismatch");
        } else {
            mail.getMessage().setHeader(SMIME_STATUS_HEADER, "Bad signature");
        }
    }

    private List<SMIMESignerInfo> acceptedSigners(List<SMIMESignerInfo> signers) {
        return signers.stream()
            .filter(info -> info.isSignValid() && (!onlyTrusted || info.getCertPath() != null))
            .toList();
    }

    /**
     * RFC 8550 section 3: the signer certificate names the sender, the From or the Sender of the message. A
     * certificate naming no e-mail address is accepted: the check applies only when it names some.
     */
    private List<SMIMESignerInfo> senderSigners(List<SMIMESignerInfo> signers, MimeMessage message) {
        List<MailAddress> senders = senders(message);
        return signers.stream()
            .filter(info -> {
                List<MailAddress> signerAddresses = SignerAddresses.of(info.getSignerCertificate());
                return signerAddresses.isEmpty() || signerAddresses.stream()
                    .anyMatch(signerAddress -> senders.stream().anyMatch(sender -> SignerAddresses.sameAddress(signerAddress, sender)));
            })
            .toList();
    }

    /**
     * The From and the Sender of the message: a signer matching either of them is accepted (RFC 8550 section 3). A
     * malformed header names no sender.
     *
     * Mail readers may display other addresses than the ones Jakarta Mail reads: a message with several From or Sender
     * fields, or a field name that mime4j only reads as From or Sender, names no sender, and an address is a sender
     * only when mime4j (used for instance by JMAP Email/get) reads it too.
     */
    private List<MailAddress> senders(MimeMessage message) {
        if (hasAmbiguousSenderFields(message)) {
            return List.of();
        }
        Set<String> mime4jAddresses = Stream.of("From", "Sender")
            .flatMap(name -> mime4jAddresses(message, name))
            .map(SMIMECheckSignature::comparable)
            .collect(ImmutableSet.toImmutableSet());
        return Stream.concat(from(message).stream(), sender(message).stream())
            .filter(InternetAddress.class::isInstance)
            .map(address -> ((InternetAddress) address).getAddress())
            .filter(address -> address != null && mime4jAddresses.contains(comparable(address)))
            .flatMap(address -> SignerAddresses.parse(address).stream())
            .toList();
    }

    /**
     * Jakarta Mail keeps the quotes of a quoted local part, mime4j drops them.
     */
    private static String comparable(String address) {
        int at = address.lastIndexOf('@');
        String localPart = at < 0 ? address : address.substring(0, at);
        if (localPart.length() >= 2 && localPart.startsWith("\"") && localPart.endsWith("\"")) {
            localPart = localPart.substring(1, localPart.length() - 1);
        }
        return (at < 0 ? localPart : localPart + address.substring(at)).toLowerCase(Locale.ROOT);
    }

    private boolean hasAmbiguousSenderFields(MimeMessage message) {
        try {
            return SubmittedHeaderFields.violation(message)
                .filter(violation -> violation != SubmittedHeaderFields.Violation.INVALID_FIELD_NAME)
                .isPresent()
                || Collections.list(message.getAllHeaderLines()).stream().anyMatch(SMIMECheckSignature::readsAsAnotherSenderField);
        } catch (MessagingException e) {
            LOGGER.info("Could not read the header fields of a signed message", e);
            return true;
        }
    }

    /**
     * A field that Jakarta Mail does not name From or Sender, while mime4j does (eg "From(x):").
     */
    private static boolean readsAsAnotherSenderField(String line) {
        int colon = line.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        String jakartaName = line.substring(0, colon).trim();
        if (jakartaName.equalsIgnoreCase("From") || jakartaName.equalsIgnoreCase("Sender")) {
            return false;
        }
        try {
            String mime4jName = RawFieldParser.DEFAULT.parseField(ContentUtil.encode(line)).getName();
            return mime4jName.equalsIgnoreCase("From") || mime4jName.equalsIgnoreCase("Sender");
        } catch (MimeException | RuntimeException e) {
            return false;
        }
    }

    private Stream<String> mime4jAddresses(MimeMessage message, String name) {
        try {
            String field = message.getHeader(name, null);
            if (field == null) {
                return Stream.empty();
            }
            // As text, not as bytes: an internationalized address keeps its characters
            return LenientAddressParser.DEFAULT.parseAddressList(MimeUtil.unfold(field))
                .flatten()
                .stream()
                .map(Mailbox::getAddress);
        } catch (MessagingException | RuntimeException e) {
            LOGGER.info("Could not parse the {} of a signed message", name, e);
            return Stream.empty();
        }
    }

    private List<Address> from(MimeMessage message) {
        try {
            return Optional.ofNullable(message.getFrom()).map(List::of).orElse(List.of());
        } catch (MessagingException e) {
            LOGGER.info("Could not parse the From of a signed message", e);
            return List.of();
        }
    }

    private Optional<Address> sender(MimeMessage message) {
        try {
            return Optional.ofNullable(message.getSender());
        } catch (MessagingException e) {
            LOGGER.info("Could not parse the Sender of a signed message", e);
            return Optional.empty();
        }
    }

    private ArrayList<AttributeValue<?>> signerInfoList(List<SMIMESignerInfo> signers) {
        ArrayList<AttributeValue<?>> signerinfolist = new ArrayList<>();

        for (SMIMESignerInfo info : signers) {
            try {
                signerinfolist.add(AttributeValue.of(info.getSignerCertificate().getEncoded()));
            } catch (CertificateEncodingException e) {
                LOGGER.warn("Failed to encode certificate", e);
            }
        }
        return signerinfolist;
    }

    private void stripSignature(Mail mail, MimeMessage message, MimeBodyPart strippedMessage) throws MessagingException {
        try {
            Object obj = strippedMessage.getContent();
            if (obj instanceof Multipart) {
                message.setContent((Multipart) obj);
            } else {
                message.setContent(obj, strippedMessage.getContentType());
            }
            message.saveChanges();
            mail.setMessage(message);
        } catch (Exception e) {
            throw new MessagingException("Error during the extraction of the signed content from the message.", e);
        }
    }

    private SMIMESigned asSMIMESigned(MimeMessage message) throws IOException, MessagingException, CMSException, SMIMEException {
        if (!SMIMESignedMessages.isSigned(message)) {
            return null;
        }
        Object obj = message.getContent();
        SMIMESigned signed;
        if (obj instanceof MimeMultipart) {
            signed = new SMIMESigned((MimeMultipart) message.getContent());
        } else if (obj instanceof SMIMESigned) {
            signed = (SMIMESigned) obj;
        } else if (obj instanceof byte[] || obj instanceof BASE64DecoderStream) {
            signed = new SMIMESigned(message);
        } else {
            signed = null;
        }
        return signed;
    }
}
