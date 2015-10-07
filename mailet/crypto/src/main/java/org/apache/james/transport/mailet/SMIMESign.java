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

import org.apache.mailet.Mail;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import java.io.IOException;

/**
 * <p>Puts a <I>server-side</I> SMIME signature on a message.
 * It is a concrete subclass of {@link Sign}, with very few modifications to it,
 * to specialize for SMIME.</p>
 *
 *  <P>Handles the following init parameters (will comment only the differences from {@link AbstractSign}):</P>
 * <ul>
 * <li>&lt;debug&gt;.</li>
 * <li>&lt;keyStoreFileName&gt;.</li>
 * <li>&lt;keyStorePassword&gt;.</li>
 * <li>&lt;keyAlias&gt;.</li>
 * <li>&lt;keyAliasPassword&gt;.</li>
 * <li>&lt;keyStoreType&gt;.</li>
 * <li>&lt;postmasterSigns&gt;. The default is <CODE>true</CODE>.</li>
 * <li>&lt;rebuildFrom&gt;. The default is <CODE>true</CODE>.</li>
 * <li>&lt;signerName&gt;.</li>
 * <li>&lt;explanationText&gt;. There is a default explanation string template in English,
 * displaying also all the headers of the original message (see {@link #getExplanationText}).</li>
 * </ul>
 * @version CVS $Revision$ $Date$
 * @since 2.3.0
 */
public class SMIMESign extends Sign {
    
    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "SMIME Signature Mailet";
    }
    
    /**
     *
     */
    protected  String[] getAllowedInitParameters() {
        String[] allowedArray = {
            "debug",
            "keyStoreFileName",
            "keyStorePassword",
            "keyStoreType",
            "keyAlias",
            "keyAliasPassword",
            "signerName",
            "postmasterSigns",
            "rebuildFrom",
            "explanationText"
        };
        return allowedArray;
    }
    
     /* ******************************************************************** */
    /* ****************** Begin of setters and getters ******************** */
    /* ******************************************************************** */    
    
    /**
     * Gets text offering an explanation.
     * If the <CODE>&lt;explanationText&gt;</CODE> init parameter is missing
     * returns the following default explanation template string:
     * <pre><code>
     * The message this file is attached to has been signed on the server by
     *     "[signerName]" <[signerAddress]>
     * to certify that the sender is known and truly has the following address (reverse-path):
     *     [reversePath]
     * and that the original message has the following message headers:
     *
     * [headers]
     *
     * The signature envelopes this attachment too.
     * Please check the signature integrity.
     *
     *     "[signerName]" <[signerAddress]>
     * </code></pre>
     */
    public String getExplanationText() {
        String explanationText = super.getExplanationText();
        if (explanationText == null) {
            explanationText = "The message this file is attached to has been signed on the server by\r\n"
            + "\t\"[signerName]\" <[signerAddress]>"
            + "\r\nto certify that the sender is known and truly has the following address (reverse-path):\r\n"
            + "\t[reversePath]"
            + "\r\nand that the original message has the following message headers:\r\n"
            + "\r\n[headers]"
            + "\r\n\r\nThe signature envelopes this attachment too."
            + "\r\nPlease check the signature integrity."
            + "\r\n\r\n"
            + "\t\"[signerName]\" <[signerAddress]>";
        }
        
        return explanationText;
    }
    
    /**
     * Initializer for property keyHolderClass.
     * Hardcodes it to {@link org.apache.james.transport.SMIMEKeyHolder}.
     */
    protected void initKeyHolderClass() throws MessagingException {
        String keyHolderClassName = "org.apache.james.security.SMIMEKeyHolder";
        try {
            setKeyHolderClass(Class.forName(keyHolderClassName));
        } catch (ClassNotFoundException cnfe) {
            throw new MessagingException(keyHolderClassName + "does not exist.");
        }
        if (isDebug()) {
            log("keyHolderClass: " + getKeyHolderClass());
        }
    }
    
    /**
     * If the <CODE>&lt;postmasterSigns&gt;</CODE> init parameter is missing sets it to <I>true</I>.
     */
    protected void initPostmasterSigns() {
        setPostmasterSigns((getInitParameter("postmasterSigns") == null) ? true : Boolean.valueOf(getInitParameter("postmasterSigns")));
    }
    
    /**
     * If the <CODE>&lt;rebuildFrom&gt;</CODE> init parameter is missing sets it to <I>true</I>.
     */
    protected void initRebuildFrom() throws MessagingException {
        setRebuildFrom((getInitParameter("rebuildFrom") == null) ? true : Boolean.valueOf(getInitParameter("rebuildFrom")));
        if (isDebug()) {
            if (isRebuildFrom()) {
                log("Will modify the \"From:\" header.");
            } else {
                log("Will leave the \"From:\" header unchanged.");
            }
        }
    }
    
    /* ******************************************************************** */
    /* ****************** End of setters and getters ********************** */
    /* ******************************************************************** */
    
    /**
     * A text file with the massaged contents of {@link #getExplanationText}
     * is attached to the original message.
     */    
    protected MimeBodyPart getWrapperBodyPart(Mail mail) throws MessagingException, IOException {
        
        String explanationText = getExplanationText();
        
        // if there is no explanation text there should be no wrapping
        if (explanationText == null) {
            return null;
        }

            MimeMessage originalMessage = mail.getMessage();

            MimeBodyPart messagePart = new MimeBodyPart();
            MimeBodyPart signatureReason = new MimeBodyPart();
            
            String contentType = originalMessage.getContentType();
            Object content = originalMessage.getContent();
            
            if (contentType != null && content != null) {
            messagePart.setContent(content, contentType);
            } else {
                throw new MessagingException("Either the content type or the content is null");
            }
            
            String headers = getMessageHeaders(originalMessage);
            
            signatureReason.setText(getReplacedExplanationText(getExplanationText(),
                                                               getSignerName(),
                                                               getKeyHolder().getSignerAddress(),
                                                               mail.getSender().toString(),
                                                               headers));
            
            signatureReason.setFileName("SignatureExplanation.txt");
            
            MimeMultipart wrapperMultiPart = new MimeMultipart();
            
            wrapperMultiPart.addBodyPart(messagePart);
            wrapperMultiPart.addBodyPart(signatureReason);
            
            MimeBodyPart wrapperBodyPart = new MimeBodyPart();
            
            wrapperBodyPart.setContent(wrapperMultiPart);
            
            return wrapperBodyPart;
    }
        
}

