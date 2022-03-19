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
import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.ParseException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.transport.KeyHolder;
import org.apache.james.transport.SMIMEAttributeNames;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

/**
 * <P>Abstract mailet providing common SMIME signature services.
 * It can be subclassed to make authoring signing mailets simple.
 * By extending it and overriding one or more of the following methods a new behaviour can
 * be quickly created without the author having to address any issue other than
 * the relevant one:</P>
 * <ul>
 * <li>{@link #initDebug}, {@link #setDebug} and {@link #isDebug} manage the debugging mode.</li>
 * <li>{@link #initExplanationText}, {@link #setExplanationText} and {@link #getExplanationText} manage the text of
 * an attachment that will be added to explain the meaning of this server-side signature.</li>
 * <li>{@link #initKeyHolder}, {@link #setKeyHolder} and {@link #getKeyHolder} manage the {@link KeyHolder} object that will
 * contain the keys and certificates and will do the crypto work.</li>
 * <li>{@link #initPostmasterSigns}, {@link #setPostmasterSigns} and {@link #isPostmasterSigns}
 * determines whether messages originated by the Postmaster will be signed or not.</li>
 * <li>{@link #initRebuildFrom}, {@link #setRebuildFrom} and {@link #isRebuildFrom}
 * determines whether the "From:" header will be rebuilt to neutralize the wrong behaviour of
 * some MUAs like Microsoft Outlook Express.</li>
 * <li>{@link #initSignerName}, {@link #setSignerName} and {@link #getSignerName} manage the name
 * of the signer to be shown in the explanation text.</li>
 * <li>{@link #isOkToSign} controls whether the mail can be signed or not.</li>
 * <li>The abstract method {@link #getWrapperBodyPart} returns the massaged {@link jakarta.mail.internet.MimeBodyPart}
 * that will be signed, or null if the message has to be signed "as is".</li>
 * </ul>
 *
 * <P>Handles the following init parameters:</P>
 * <ul>
 * <li>&lt;keyHolderClass&gt;: Sets the class of the KeyHolder object that will handle the cryptography functions,
 * for example org.apache.james.security.SMIMEKeyHolder for SMIME.</li>
 * <li>&lt;debug&gt;: if <CODE>true</CODE> some useful information is logged.
 * The default is <CODE>false</CODE>.</li>
 * <li>&lt;keyStoreFileName&gt;: the {@link java.security.KeyStore} full file name.</li>
 * <li>&lt;keyStorePassword&gt;: the <CODE>KeyStore</CODE> password.
 *      If given, it is used to check the integrity of the keystore data,
 *      otherwise, if null, the integrity of the keystore is not checked.</li>
 * <li>&lt;keyAlias&gt;: the alias name to use to search the Key using {@link java.security.KeyStore#getKey}.
 * The default is to look for the first and only alias in the keystore;
 * if zero or more than one is found a {@link java.security.KeyStoreException} is thrown.</li>
 * <li>&lt;keyAliasPassword&gt;: the alias password. The default is to use the <CODE>KeyStore</CODE> password.
 *      At least one of the passwords must be provided.</li>
 * <li>&lt;keyStoreType&gt;: the type of the keystore. The default will use {@link java.security.KeyStore#getDefaultType}.</li>
 * <li>&lt;postmasterSigns&gt;: if <CODE>true</CODE> the message will be signed even if the sender is the Postmaster.
 * The default is <CODE>false</CODE>.</li></li>
 * <li>&lt;rebuildFrom&gt;: If <CODE>true</CODE> will modify the "From:" header.
 * For more info see {@link #isRebuildFrom}.
 * The default is <CODE>false</CODE>.</li>
 * <li>&lt;signerName&gt;: the name of the signer to be shown in the explanation text.
 * The default is to use the "CN=" property of the signing certificate.</li>
 * <li>&lt;explanationText&gt;: the text of an explanation of the meaning of this server-side signature.
 * May contain the following substitution patterns (see also {@link #getReplacedExplanationText}):
 * <CODE>[signerName]</CODE>, <CODE>[signerAddress]</CODE>, <CODE>[reversePath]</CODE>, <CODE>[headers]</CODE>.
 * It should be included in the signature.
 * The actual presentation of the text depends on the specific concrete mailet subclass:
 * see for example {@link SMIMESign}.
 * The default is to not have any explanation text.</li>
 * </ul>
 * @version CVS $Revision$ $Date$
 * @since 2.2.1
 */
public abstract class AbstractSign extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSign.class);
    
    private static final String HEADERS_PATTERN = "[headers]";
    
    private static final String SIGNER_NAME_PATTERN = "[signerName]";
    
    private static final String SIGNER_ADDRESS_PATTERN = "[signerAddress]";
    
    private static final String REVERSE_PATH_PATTERN = "[reversePath]";
    
    /**
     * Holds value of property debug.
     */
    private boolean debug;
    
    /**
     * Holds value of property keyHolderClass.
     */
    private Class<?> keyHolderClass;
    
    /**
     * Holds value of property explanationText.
     */
    private String explanationText;
    
    /**
     * Holds value of property keyHolder.
     */
    private KeyHolder keyHolder;
    
    /**
     * Holds value of property postmasterSigns.
     */
    private boolean postmasterSigns;
    
    /**
     * Holds value of property rebuildFrom.
     */
    private boolean rebuildFrom;
    
    /**
     * Holds value of property signerName.
     */
    private String signerName;

    @Inject
    private UsersRepository usersRepository;
    
    /**
     * Gets the expected init parameters.
     * @return A set containing the parameter names allowed for this mailet.
     */
    protected abstract Set<String> getAllowedInitParameters();
    
    /* ******************************************************************** */
    /* ****************** Begin of setters and getters ******************** */
    /* ******************************************************************** */
    
    /**
     * Initializer for property debug.
     */
    protected void initDebug() {
        setDebug((getInitParameter("debug") == null) ? false : Boolean.parseBoolean(getInitParameter("debug")));
    }
    
    /**
     * Getter for property debug.
     * @return Value of property debug.
     */
    public boolean isDebug() {
        return this.debug;
    }
    
    /**
     * Setter for property debug.
     * @param debug New value of property debug.
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }
    
    /**
     * Initializer for property keyHolderClass.
     */
    protected void initKeyHolderClass() throws MessagingException {
        String keyHolderClassName = getInitParameter("keyHolderClass");
        if (keyHolderClassName == null) {
            throw new MessagingException("<keyHolderClass> parameter missing.");
        }
        try {
            setKeyHolderClass(Class.forName(keyHolderClassName));
        } catch (ClassNotFoundException cnfe) {
            throw new MessagingException("The specified <keyHolderClass> does not exist: " + keyHolderClassName);
        }
        if (isDebug()) {
            LOGGER.debug("keyHolderClass: {}", getKeyHolderClass());
        }
    }
    
    /**
     * Getter for property keyHolderClass.
     * @return Value of property keyHolderClass.
     */
    public Class<?> getKeyHolderClass() {
        return this.keyHolderClass;
    }
    
    /**
     * Setter for property keyHolderClass.
     * @param keyHolderClass New value of property keyHolderClass.
     */
    public void setKeyHolderClass(Class<?> keyHolderClass) {
        this.keyHolderClass = keyHolderClass;
    }
    
    /**
     * Initializer for property explanationText.
     */
    protected void initExplanationText() {
        setExplanationText(getInitParameter("explanationText"));
        if (isDebug() && LOGGER.isDebugEnabled()) {
            LOGGER.debug("Explanation text:\r\n" + getExplanationText());
        }
    }
    
    /**
     * Getter for property explanationText.
     * Text to be used in the SignatureExplanation.txt file.
     * @return Value of property explanationText.
     */
    public String getExplanationText() {
        return this.explanationText;
    }
    
    /**
     * Setter for property explanationText.
     * @param explanationText New value of property explanationText.
     */
    public void setExplanationText(String explanationText) {
        this.explanationText = explanationText;
    }
    
    /**
     * Initializer for property keyHolder.
     */
    protected void initKeyHolder() throws Exception {
        Constructor<?> keyHolderConstructor;
        try {
            keyHolderConstructor = keyHolderClass.getConstructor(String.class, String.class, String.class, String.class, String.class);
        } catch (NoSuchMethodException nsme) {
            throw new MessagingException("The needed constructor does not exist: "
                    + keyHolderClass + "(String, String, String, String, String)");
        }
        
        
        String keyStoreFileName = getInitParameter("keyStoreFileName");
        if (keyStoreFileName == null) {
            throw new MessagingException("<keyStoreFileName> parameter missing.");
        }
        
        String keyStorePassword = getInitParameter("keyStorePassword");
        if (keyStorePassword == null) {
            throw new MessagingException("<keyStorePassword> parameter missing.");
        }
        String keyAliasPassword = getInitParameter("keyAliasPassword");
        if (keyAliasPassword == null) {
            keyAliasPassword = keyStorePassword;
            if (isDebug()) {
                LOGGER.debug("<keyAliasPassword> parameter not specified: will default to the <keyStorePassword> parameter.");
            }
        }
        
        String keyStoreType = getInitParameter("keyStoreType");
        if (keyStoreType == null) {
            if (isDebug()) {
                LOGGER.debug("<keyStoreType> parameter not specified: the default will be as appropriate to the keyStore requested.");
            }
        }
        
        String keyAlias = getInitParameter("keyAlias");
        if (keyAlias == null) {
            if (isDebug()) {
                LOGGER.debug("<keyAlias> parameter not specified: will look for the first one in the keystore.");
            }
        }
        
        if (isDebug()) {
            LOGGER.debug("KeyStore related parameters: keyStoreFileName={}, keyStoreType={}, keyAlias={}", keyStoreFileName, keyStoreType, keyAlias);
        }
            
        // Certificate preparation
        Object[] parameters = {keyStoreFileName, keyStorePassword, keyAlias, keyAliasPassword, keyStoreType};
        setKeyHolder((KeyHolder)keyHolderConstructor.newInstance(parameters));
        
        if (isDebug()) {
            LOGGER.debug("Subject Distinguished Name: {}", getKeyHolder().getSignerDistinguishedName());
        }
        
        if (getKeyHolder().getSignerAddress() == null) {
            throw new MessagingException("Signer address missing in the certificate.");
        }
    }
    
    /**
     * Getter for property keyHolder.
     * It is <CODE>protected</CODE> instead of <CODE>public</CODE> for security reasons.
     * @return Value of property keyHolder.
     */
    protected KeyHolder getKeyHolder() {
        return this.keyHolder;
    }
    
    /**
     * Setter for property keyHolder.
     * It is <CODE>protected</CODE> instead of <CODE>public</CODE> for security reasons.
     * @param keyHolder New value of property keyHolder.
     */
    protected void setKeyHolder(KeyHolder keyHolder) {
        this.keyHolder = keyHolder;
    }
    
    /**
     * Initializer for property postmasterSigns.
     */
    protected void initPostmasterSigns() {
        setPostmasterSigns((getInitParameter("postmasterSigns") == null) ? false : Boolean.parseBoolean(getInitParameter("postmasterSigns")));
    }
    
    /**
     * Getter for property postmasterSigns.
     * If true will sign messages signed by the postmaster.
     * @return Value of property postmasterSigns.
     */
    public boolean isPostmasterSigns() {
        return this.postmasterSigns;
    }
    
    /**
     * Setter for property postmasterSigns.
     * @param postmasterSigns New value of property postmasterSigns.
     */
    public void setPostmasterSigns(boolean postmasterSigns) {
        this.postmasterSigns = postmasterSigns;
    }
    
    /**
     * Initializer for property rebuildFrom.
     */
    protected void initRebuildFrom() throws MessagingException {
        setRebuildFrom((getInitParameter("rebuildFrom") == null) ? false : Boolean.parseBoolean(getInitParameter("rebuildFrom")));
        if (isDebug()) {
            if (isRebuildFrom()) {
                LOGGER.debug("Will modify the \"From:\" header.");
            } else {
                LOGGER.debug("Will leave the \"From:\" header unchanged.");
            }
        }
    }
    
    /**
     * Getter for property rebuildFrom.
     * If true will modify the "From:" header.
     * <P>The modification is as follows:
     * assuming that the signer mail address in the signer certificate is <I>trusted-server@xxx.com&gt;</I>
     * and that <I>From: "John Smith" <john.smith@xxx.com></I>
     * we will get <I>From: "John Smith" <john.smith@xxx.com>" &lt;trusted-server@xxx.com&gt;</I>.</P>
     * <P>If the "ReplyTo:" header is missing or empty it will be set to the original "From:" header.</P>
     * <P>Such modification is necessary to achieve a correct behaviour
     * with some mail clients (e.g. Microsoft Outlook Express).</P>
     * @return Value of property rebuildFrom.
     */
    public boolean isRebuildFrom() {
        return this.rebuildFrom;
    }
    
    /**
     * Setter for property rebuildFrom.
     * @param rebuildFrom New value of property rebuildFrom.
     */
    public void setRebuildFrom(boolean rebuildFrom) {
        this.rebuildFrom = rebuildFrom;
    }
    
    /**
     * Initializer for property signerName.
     */
    protected void initSignerName() {
        setSignerName(getInitParameter("signerName"));
        if (getSignerName() == null) {
            if (getKeyHolder() == null) {
                throw new RuntimeException("initKeyHolder() must be invoked before initSignerName()");
            }
            setSignerName(getKeyHolder().getSignerCN());
            if (isDebug()) {
                LOGGER.debug("<signerName> parameter not specified: will use the certificate signer \"CN=\" attribute.");
            }
        }
    }
    
    /**
     * Getter for property signerName.
     * @return Value of property signerName.
     */
    public String getSignerName() {
        return this.signerName;
    }
    
    /**
     * Setter for property signerName.
     * @param signerName New value of property signerName.
     */
    public void setSignerName(String signerName) {
        this.signerName = signerName;
    }
    
    /* ******************************************************************** */
    /* ****************** End of setters and getters ********************** */
    /* ******************************************************************** */    
    
    @Override
    public void init() throws MessagingException {
        
        // check that all init parameters have been declared in allowedInitParameters
        checkInitParameters(getAllowedInitParameters());
        
        try {
            initDebug();
            if (isDebug()) {
                LOGGER.debug("Initializing");
            }
            
            initKeyHolderClass();
            initKeyHolder();
            initSignerName();
            initPostmasterSigns();
            initRebuildFrom();
            initExplanationText();
            
            
        } catch (MessagingException me) {
            throw me;
        } catch (Exception e) {
            LOGGER.error("Exception thrown", e);
            throw new MessagingException("Exception thrown", e);
        } finally {
            if (isDebug()) {
                LOGGER.debug("Other parameters: signerName={}, postmasterSigns={}, rebuildFrom={}", getSignerName(), postmasterSigns, rebuildFrom);
            }
        }
        
    }
    
    /**
     * Service does the hard work, and signs
     *
     * @param mail the mail to sign
     * @throws MessagingException if a problem arises signing the mail
     */
    @Override
    public void service(Mail mail) throws MessagingException {
        
        try {
            if (!isOkToSign(mail)) {
                return;
            }
            
            MimeBodyPart wrapperBodyPart = getWrapperBodyPart(mail);
            
            MimeMessage originalMessage = mail.getMessage();
            
            // do it
            MimeMultipart signedMimeMultipart;
            if (wrapperBodyPart != null) {
                signedMimeMultipart = getKeyHolder().generate(wrapperBodyPart);
            } else {
                signedMimeMultipart = getKeyHolder().generate(originalMessage);
            }
            
            MimeMessage newMessage = new MimeMessage(Session.getDefaultInstance(System.getProperties(),
            null));
            Enumeration<String> headerEnum = originalMessage.getAllHeaderLines();
            while (headerEnum.hasMoreElements()) {
                newMessage.addHeaderLine(headerEnum.nextElement());
            }
            
            newMessage.setSender(new InternetAddress(getKeyHolder().getSignerAddress(), getSignerName()));
  
            if (isRebuildFrom()) {
                // builds a new "mixed" "From:" header
                InternetAddress modifiedFromIA = new InternetAddress(getKeyHolder().getSignerAddress(), mail.getMaybeSender().asString());
                newMessage.setFrom(modifiedFromIA);
                
                // if the original "ReplyTo:" header is missing sets it to the original "From:" header
                newMessage.setReplyTo(originalMessage.getReplyTo());
            }
            
            newMessage.setContent(signedMimeMultipart, signedMimeMultipart.getContentType());
            String messageId = originalMessage.getMessageID();
            newMessage.saveChanges();
            if (messageId != null) {
                newMessage.setHeader(RFC2822Headers.MESSAGE_ID, messageId);
            }
            
            mail.setMessage(newMessage);
            
            // marks this mail as server-signed
            mail.setAttribute(new Attribute(SMIMEAttributeNames.SMIME_SIGNING_MAILET, AttributeValue.of(this.getClass().getName())));
            // it is valid for us by definition (signed here by us)
            mail.setAttribute(new Attribute(SMIMEAttributeNames.SMIME_SIGNATURE_VALIDITY, AttributeValue.of("valid")));
            
            // saves the trusted server signer address
            // warning: should be same as the mail address in the certificate, but it is not guaranteed
            mail.setAttribute(new Attribute(SMIMEAttributeNames.SMIME_SIGNER_ADDRESS, AttributeValue.of(getKeyHolder().getSignerAddress())));
            
            if (isDebug()) {
                LOGGER.debug("Message signed, reverse-path: {}, Id: {}", mail.getMaybeSender().asString(), messageId);
            }
            
        } catch (MessagingException me) {
            LOGGER.error("MessagingException found - could not sign!", me);
            throw me;
        } catch (Exception e) {
            LOGGER.error("Exception found", e);
            throw new MessagingException("Exception thrown - could not sign!", e);
        }
        
    }
    
    
    /**
     * <P>Checks if the mail can be signed.</P>
     * <P>Rules:</P>
     * <OL>
     * <LI>The reverse-path != null (it is not a bounce).</LI>
     * <LI>The sender user must have been SMTP authenticated.</LI>
     * <LI>Either:</LI>
     * <UL>
     * <LI>The reverse-path is the postmaster address and {@link #isPostmasterSigns} returns <I>true</I></LI>
     * <LI>or the reverse-path == the authenticated user
     * and there is at least one "From:" address == reverse-path.</LI>.
     * </UL>
     * <LI>The message has not already been signed (mimeType != <I>multipart/signed</I>
     * and != <I>application/pkcs7-mime</I>).</LI>
     * </OL>
     * @param mail The mail object to check.
     * @return True if can be signed.
     */
    protected boolean isOkToSign(Mail mail) throws MessagingException {
        // Is it a bounce?
        if (!mail.hasSender()) {
            LOGGER.info("Can not sign: no sender");
            return false;
        }

        MailAddress reversePath = mail.getMaybeSender().get();

        Optional<String> fetchedAuthUser = AttributeUtils.getValueAndCastFromMail(mail, Mail.SMTP_AUTH_USER, String.class);
        // was the sender user SMTP authorized?
        if (!fetchedAuthUser.isPresent()) {
            LOGGER.info("Can not sign mail for sender <{}> as he is not a SMTP authenticated user", mail.getMaybeSender().asString());
            return false;
        }

        Username authUser = Username.of(fetchedAuthUser.get());

        // The sender is the postmaster?
        if (Objects.equal(getMailetContext().getPostmaster(), reversePath)) {
            // should not sign postmaster sent messages?
            if (!isPostmasterSigns()) {
                LOGGER.info("Can not sign mails for postmaster");
                return false;
            }
        } else {
            // is the reverse-path user different from the SMTP authorized user?
            Username username = getUsername(reversePath);
            if (!username.equals(authUser)) {
                LOGGER.info("SMTP logged in as <{}> but pretend to be sender <{}>", authUser.asString(), username.asString());
                return false;
            }
            // is there no "From:" address same as the reverse-path?
            if (!fromAddressSameAsReverse(mail)) {
                LOGGER.info("Can not sign mails with empty FROM header field");
                return false;
            }
        }

        // if already signed return false
        MimeMessage mimeMessage = mail.getMessage();
        boolean isAlreadySigned = mimeMessage.isMimeType("multipart/signed")
            || mimeMessage.isMimeType("application/pkcs7-mime");
        if (isAlreadySigned) {
            LOGGER.info("Can not sign a mail already signed");
        }
        return !isAlreadySigned;

    }

    private Username getUsername(MailAddress mailAddress) {
        try {
            return usersRepository.getUsername(mailAddress);
        } catch (UsersRepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates the {@link jakarta.mail.internet.MimeBodyPart} that will be signed.
     * For example, may attach a text file explaining the meaning of the signature,
     * or an XML file containing information that can be checked by other MTAs.
     * @param mail The mail to massage.
     * @return The massaged MimeBodyPart to sign, or null to have the whole message signed "as is".
     */    
    protected abstract MimeBodyPart getWrapperBodyPart(Mail mail) throws MessagingException, IOException;
    
    /**
     * Utility method that checks if there is at least one address in the "From:" header
     * same as the <i>reverse-path</i>.
     * @param mail The mail to check.
     * @return True if an address is found, false otherwise.
     */    
    protected final boolean fromAddressSameAsReverse(Mail mail) {
        
        if (!mail.hasSender()) {
            return false;
        }
        MailAddress reversePath = mail.getMaybeSender().get();
        
        try {
            InternetAddress[] fromArray = (InternetAddress[]) mail.getMessage().getFrom();
            if (fromArray != null) {
                for (InternetAddress aFromArray : fromArray) {
                    MailAddress mailAddress;
                    try {
                        mailAddress = new MailAddress(aFromArray);
                    } catch (ParseException pe) {
                        LOGGER.info("Unable to parse a \"FROM\" header address: {}; ignoring.", aFromArray);
                        continue;
                    }
                    if (mailAddress.equals(reversePath)) {
                        return true;
                    }
                }
            }
        } catch (MessagingException me) {
            LOGGER.info("Unable to parse the \"FROM\" header; ignoring.");
        }
        
        return false;
        
    }
    
    /**
     * Utility method for obtaining a string representation of the Message's headers
     * @param message The message to extract the headers from.
     * @return The string containing the headers.
     */
    protected final String getMessageHeaders(MimeMessage message) throws MessagingException {
        Enumeration<String> heads = message.getAllHeaderLines();
        StringBuilder headBuffer = new StringBuilder(1024);
        while (heads.hasMoreElements()) {
            headBuffer.append(heads.nextElement()).append("\r\n");
        }
        return headBuffer.toString();
    }
    
    /**
     * Prepares the explanation text making substitutions in the <I>explanationText</I> template string.
     * Utility method that searches for all occurrences of some pattern strings
     * and substitute them with the appropriate params.
     * @param explanationText The template string for the explanation text.
     * @param signerName The string that will replace the <CODE>[signerName]</CODE> pattern.
     * @param signerAddress The string that will replace the <CODE>[signerAddress]</CODE> pattern.
     * @param reversePath The string that will replace the <CODE>[reversePath]</CODE> pattern.
     * @param headers The string that will replace the <CODE>[headers]</CODE> pattern.
     * @return The actual explanation text string with all replacements done.
     */    
    protected final String getReplacedExplanationText(String explanationText, String signerName,
    String signerAddress, String reversePath, String headers) {
        
        String replacedExplanationText = explanationText;
        
        replacedExplanationText = getReplacedString(replacedExplanationText, SIGNER_NAME_PATTERN, signerName);
        replacedExplanationText = getReplacedString(replacedExplanationText, SIGNER_ADDRESS_PATTERN, signerAddress);
        replacedExplanationText = getReplacedString(replacedExplanationText, REVERSE_PATH_PATTERN, reversePath);
        replacedExplanationText = getReplacedString(replacedExplanationText, HEADERS_PATTERN, headers);
        
        return replacedExplanationText;
    }
    
    /**
     * Searches the <I>template</I> String for all occurrences of the <I>pattern</I> string
     * and creates a new String substituting them with the <I>actual</I> String.
     * @param template The template String to work on.
     * @param pattern The string to search for the replacement.
     * @param actual The actual string to use for the replacement.
     */    
    private String getReplacedString(String template, String pattern, String actual) {
         if (actual != null) {
             StringBuilder sb = new StringBuilder(template.length());
            int fromIndex = 0;
            int index;
            while ((index = template.indexOf(pattern, fromIndex)) >= 0) {
                sb.append(template, fromIndex, index);
                sb.append(actual);
                fromIndex = index + pattern.length();
            }
            if (fromIndex < template.length()) {
                sb.append(template.substring(fromIndex));
            }
            return sb.toString();
        } else {
            return template;
        }
    }
    
}

