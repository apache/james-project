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
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;

import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.james.transport.SMIMEKeyHolder;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.base.GenericMailet;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.RecipientId;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.RecipientInformationStore;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.mail.smime.SMIMEEnveloped;
import org.bouncycastle.mail.smime.SMIMEUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This mailet decrypts a s/mime encrypted message. It takes as input an
 * encrypted message and it tries to dechiper it using the key specified in its
 * configuration. If the decryption is successful the mail will be changed and
 * it will contain the decrypted message. The mail attribute
 * <code>org.apache.james.SMIMEDecrypt</code> will contain the public
 * certificate of the key used in the process. 
 * 
 * The configuration parameters of this mailet are summarized below. The firsts
 * define the keystore where the key that will be used to decrypt messages is
 * saved.
 * <ul>
 * <li>keyStoreType (default: system dependent): defines the type of the store.
 * Usually jks, pkcs12 or pkcs7</li>
 * <li>keyStoreFileName (mandatory): private key store path.</li>
 * <li>keyStorePassword (default: ""): private key store password</li>
 * </ul>
 * The other parameters define which private key have to be used. (if the store
 * contains more than one key).
 * <ul>
 * <li>keyAlias: private key alias.</li>
 * <li>keyPass: private key password</li>
 * </ul>
 * 
 */
public class SMIMEDecrypt extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(SMIMEDecrypt.class);

    private SMIMEKeyHolder keyHolder;
    private X509CertificateHolder certificateHolder;
    private AttributeName mailAttribute = AttributeName.of("org.apache.james.SMIMEDecrypt");
    
    @Override
    public void init() throws MessagingException {
        super.init();
        
        MailetConfig config = getMailetConfig();
        
        String privateStoreType = config.getInitParameter("keyStoreType");
        
        String privateStoreFile = config.getInitParameter("keyStoreFileName");
        if (privateStoreFile == null) {
            throw new MessagingException("No keyStoreFileName specified");
        }
        
        String privateStorePass = config.getInitParameter("keyStorePassword");
        
        String keyAlias = config.getInitParameter("keyAlias");
        String keyPass = config.getInitParameter("keyAliasPassword");
        
        String mailAttributeConf = config.getInitParameter("mailAttribute");
        if (mailAttributeConf != null) {
            mailAttribute = AttributeName.of(mailAttributeConf);
        }
        
        try {
            keyHolder = new SMIMEKeyHolder(privateStoreFile, privateStorePass, keyAlias, keyPass, privateStoreType);
        } catch (IOException | GeneralSecurityException e) {
            throw new MessagingException("Error loading keystore", e);
        }

        certificateHolder = from(keyHolder.getCertificate());
    }

    private X509CertificateHolder from(X509Certificate certificate) throws MessagingException {
        try {
            return new X509CertificateHolder(certificate.getEncoded());
        } catch (CertificateEncodingException | IOException e) {
            throw new MessagingException("Error during the parsing of the certificate", e);
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void service(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();
        Part strippedMessage = null;
        LOGGER.info("Starting message decryption..");
        if (message.isMimeType("application/x-pkcs7-mime") || message.isMimeType("application/pkcs7-mime")) {
            try {
                SMIMEEnveloped env = new SMIMEEnveloped(message);
                RecipientInformationStore informationStore = env.getRecipientInfos();
                Collection<RecipientInformation> recipients = informationStore.getRecipients();
                for (RecipientInformation info : recipients) {
                    RecipientId id = info.getRID();
                    if (id.match(certificateHolder)) {
                        try {
                            JceKeyTransEnvelopedRecipient recipient = new JceKeyTransEnvelopedRecipient(keyHolder.getPrivateKey());
                            // strippedMessage contains the decrypted message.
                            strippedMessage = SMIMEUtil.toMimeBodyPart(info.getContent(recipient));
                            LOGGER.info("Encrypted message decrypted");
                        } catch (Exception e) {
                            throw new MessagingException("Error during the decryption of the message", e);
                        }
                    } else {
                        LOGGER.info("Found an encrypted message but it isn't encrypted for the supplied key");
                    }
                }
            } catch (CMSException e) {
                throw new MessagingException("Error during the decryption of the message", e);
            }
        }

        // if the decryption has been successful..
        if (strippedMessage != null) {
            // I put the private key's public certificate as a mailattribute.
            // I create a list of certificate because I want to minic the
            // behavior of the SMIMEVerifySignature mailet. In that way
            // it is possible to reuse the same matchers to analyze
            // the result of the operation.
            ArrayList<AttributeValue<?>> list = new ArrayList<>(1);
            try {
                list.add(AttributeValue.of(keyHolder.getCertificate().getEncoded()));
            } catch (CertificateEncodingException e) {
                LOGGER.warn("Failed to encode certificate", e);
            }
            mail.setAttribute(new Attribute(mailAttribute, AttributeValue.of(list)));

            // I start the message stripping.
            try {
                MimeMessage newMessage = new MimeMessage(message);
                newMessage.setText(text(strippedMessage), StandardCharsets.UTF_8.name());
                if (!strippedMessage.isMimeType("multipart/*")) {
                    newMessage.setDisposition(null);
                }
                newMessage.saveChanges();
                mail.setMessage(newMessage);
            } catch (IOException e) {
                LOGGER.error("Error during the strip of the encrypted message", e);
                throw new MessagingException("Error during the stripping of the encrypted message",e);
            }
        }
    }

    private String text(Part mimePart) throws IOException, MessagingException {
        return IOUtils.toString(mimePart.getDataHandler().getInputStream(), StandardCharsets.UTF_8);
    }
}
