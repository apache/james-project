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



package org.apache.james.transport.mailet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;

import org.apache.james.transport.SMIMEKeyHolder;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.RecipientId;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.mail.smime.SMIMEEnveloped;
import org.bouncycastle.mail.smime.SMIMEUtil;

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

    private SMIMEKeyHolder keyHolder;
    protected String mailAttribute = "org.apache.james.SMIMEDecrypt";
    
    public void init() throws MessagingException {
        super.init();
        
        MailetConfig config = getMailetConfig();
        
        String privateStoreType = config.getInitParameter("keyStoreType");
        
        String privateStoreFile = config.getInitParameter("keyStoreFileName");
        if (privateStoreFile == null) throw new MessagingException("No keyStoreFileName specified");
        
        String privateStorePass = config.getInitParameter("keyStorePassword");
        
        String keyAlias= config.getInitParameter("keyAlias");
        String keyPass = config.getInitParameter("keyAliasPassword");
        
        String mailAttributeConf = config.getInitParameter("mailAttribute");
        if (mailAttributeConf != null) mailAttribute = mailAttributeConf;
        
        try {
            keyHolder = new SMIMEKeyHolder(privateStoreFile, privateStorePass, keyAlias, keyPass, privateStoreType);
        } catch (IOException e) {
            throw new MessagingException("Error loading keystore", e);
        } catch (GeneralSecurityException e) {
            throw new MessagingException("Error loading keystore", e);
        }

        
    }
    
    /**
     * @see org.apache.mailet.Mailet#service(org.apache.mailet.Mail)
     */
    public void service(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();
        Part strippedMessage = null;
        log("Starting message decryption..");
        if (message.isMimeType("application/x-pkcs7-mime") || message.isMimeType("application/pkcs7-mime")) {
            try {
                SMIMEEnveloped env = new SMIMEEnveloped(message);
                Collection<RecipientInformation> recipients = env.getRecipientInfos().getRecipients();
                Iterator<RecipientInformation> iter = recipients.iterator();
                while (iter.hasNext()) {
                    RecipientInformation info = iter.next();
                    RecipientId id = info.getRID();
                    if (id.match(keyHolder.getCertificate())) {
                        try {
                            MimeBodyPart part = SMIMEUtil.toMimeBodyPart(info.getContent(keyHolder.getPrivateKey(), "BC"));
                            // strippedMessage contains the decrypted message.
                            strippedMessage = part;
                            log("Encrypted message decrypted");
                        } catch (Exception e) {
                            throw new MessagingException("Error during the decryption of the message", e); }
                    } else {
                        log("Found an encrypted message but it isn't encrypted for the supplied key");
                    }
                }
            } catch (CMSException e) { throw new MessagingException("Error during the decryption of the message",e); }
        }
        
        // if the decryption has been successful..
        if (strippedMessage != null) {
            // I put the private key's public certificate as a mailattribute.
            // I create a list of certificate because I want to minic the
            // behavior of the SMIMEVerifySignature mailet. In that way
            // it is possible to reuse the same matchers to analyze
            // the result of the operation.
            ArrayList<X509Certificate> list = new ArrayList<X509Certificate>(1);
            list.add(keyHolder.getCertificate());
            mail.setAttribute(mailAttribute, list);

            // I start the message stripping.
            try {
                MimeMessage newmex = new MimeMessage(message);
                Object obj = strippedMessage.getContent();
                if (obj instanceof Multipart) {
                    log("The message is multipart, content type "+((Multipart)obj).getContentType());
                    newmex.setContent((Multipart)obj);
                } else {
                    newmex.setContent(obj, strippedMessage.getContentType());
                    newmex.setDisposition(null);
                }
                newmex.saveChanges();
                mail.setMessage(newmex);
            } catch (IOException e) { 
                log("Error during the strip of the encrypted message");
                throw new MessagingException("Error during the stripping of the encrypted message",e);
            }
        }
    }
}
