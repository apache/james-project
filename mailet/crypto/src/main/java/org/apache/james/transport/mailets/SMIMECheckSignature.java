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
import java.util.List;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.james.transport.KeyStoreHolder;
import org.apache.james.transport.SMIMESignerInfo;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.base.GenericMailet;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.mail.smime.SMIMEException;
import org.bouncycastle.mail.smime.SMIMESigned;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * store path.
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
 * </ul>
 * </p>
 * 
 */
public class SMIMECheckSignature extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(SMIMECheckSignature.class);

    private KeyStoreHolder trustedCertificateStore;
    private boolean stripSignature = false;
    private boolean onlyTrusted = true;
    private AttributeName mailAttribute = AttributeName.of("org.apache.james.SMIMECheckSignature");

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
        
        
        String type = config.getInitParameter("keyStoreType");
        String file = config.getInitParameter("keyStoreFileName");
        String password = config.getInitParameter("keyStorePassword");
        
        try {
            if (file != null) {
                trustedCertificateStore = new KeyStoreHolder(file, password, type);
            } else {
                LOGGER.info("No trusted store path specified, using default store.");
                trustedCertificateStore = new KeyStoreHolder(password);
            }
        } catch (Exception e) {
            throw new MessagingException("Error loading the trusted certificate store", e);
        }

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
        
        try {
            SMIMESigned signed = asSMIMESigned(message);

            if (signed != null) {
                signers = trustedCertificateStore.verifySignatures(signed);
                strippedMessage = signed.getContent();
            } else {
                LOGGER.info("Content not identified as signed");
            }
            
            // These errors are logged but they don't cause the message to change its state. The message
            // is considered as not signed and the process will go on.
        } catch (CMSException | SMIMEException e) {
            LOGGER.error("Error during the analysis of the signed message", e);
            signers = null;
        } catch (IOException e) {
            LOGGER.error("IO error during the analysis of the signed message", e);
            signers = null;
        } catch (Exception e) {
            LOGGER.error("Generic error occured during the analysis of the message", e);
            signers = null;
        }
        
        // If at least one mail signer is found 
        // the mail attributes are set.
        if (signers != null) {
            ArrayList<AttributeValue<?>> signerinfolist = signerInfoList(signers);

            if (!signerinfolist.isEmpty()) {
                mail.setAttribute(new Attribute(mailAttribute, AttributeValue.of(signerinfolist)));
            } else {
                // if no valid signers are found the message is not modified.
                strippedMessage = null;
            }
        }

        if (stripSignature && strippedMessage != null) {
            stripSignature(mail, message, strippedMessage);
        }
    }

    private ArrayList<AttributeValue<?>> signerInfoList(List<SMIMESignerInfo> signers) {
        ArrayList<AttributeValue<?>> signerinfolist = new ArrayList<>();

        for (SMIMESignerInfo info : signers) {
            if (info.isSignValid()
                    && (!onlyTrusted || info.getCertPath() != null)) {
                try {
                    signerinfolist.add(AttributeValue.of(info.getSignerCertificate().getEncoded()));
                } catch (CertificateEncodingException e) {
                    LOGGER.warn("Failed to encode certificate", e);
                }
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
        Object obj = message.getContent();
        SMIMESigned signed;
        if (obj instanceof MimeMultipart) {
            signed = new SMIMESigned((MimeMultipart) message.getContent());
        } else if (obj instanceof SMIMESigned) {
            signed = (SMIMESigned) obj;
        } else if (obj instanceof byte[]) {
            signed = new SMIMESigned(message);
        } else {
            signed = null;
        }
        return signed;
    }
}
