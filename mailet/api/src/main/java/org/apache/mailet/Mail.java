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


package org.apache.mailet;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.mailet.PerRecipientHeaders.Header;

import com.google.common.collect.ImmutableMap;

/**
 * <p>Wraps a MimeMessage with additional routing and processing information.
 * <p>This includes
 * <ul>
 * <li>a unique name</li>
 * <li>envelope properties such the SMTP-specified sender ("MAIL FROM") and recipients ("RCPT TO")</li>
 * <li>the IP address and hostname of the sending server</li>
 * <li>the processing state, which also represents the processor in
 *     the mailet container which is currently processing the message</li>
 * <li>the time at which the Mail was last updated</li>
 * <li>additional processing attributes (see below)</li>
 * </ul>
 * <p>
 * The Mail interface also defines constants for special processor names,
 * such as "root" and "error".
 * <p>
 * <b>Mail Attributes</b>
 * <p>
 * While processing a Mail instance, a Mailet can associate additional
 * information with it by using mail attributes. These attributes can
 * then be queried by the same mailet or other mailets later on.
 * <p>
 * Some containers may also use attributes to provide envelope information.
 * <p>
 * Every attribute consists of a name and a value.
 * Attribute names should follow the same convention as package names.
 * The Mailet API specification reserves names matching
 * <i>org.apache.james.*</i> and <i>org.apache.mailet.*</i>.
 * <p>
 * Attribute values can be arbitrary objects, but since Mail is
 * Serializable, the attribute value must be Serializable as well.
 * <p>
 * The list of attributes which are currently associated with a Mail
 * instance can be retrieved using the {@link #getAttributeNames}
 * method, and given its name, the value of an attribute can be
 * retrieved using the {@link #getAttribute} method. It is also
 * possible to remove {@link #removeAttribute one} attribute or
 * {@link #removeAllAttributes() all} attributes of a Mail instance.
 */
public interface Mail extends Serializable, Cloneable {
    /**
     * "ghost" state applies to mail that should no longer be processed
     */
    String GHOST = "ghost";
    /**
     * "root" state applies to mail entering mail processing
     */
    String DEFAULT = "root";
    /**
     * "error" state applies to mail whose processing fails. This is the default
     * way of handling errors.
     */
    String ERROR = "error";
    /**
     * "transport" is commonly used for expressing decisions taken on an email
     * (whether to relay it? Deliver it to local recipients? Etc...).
     */
    String TRANSPORT = "transport";
    /**
     * "local-delivery" is commonly used operations to perform for local recipients upon receiving emails.
     */
    String LOCAL_DELIVERY = "local-delivery";

    AttributeName SMTP_AUTH_USER = AttributeName.of("org.apache.james.SMTPAuthUser");
    AttributeName SMTP_HELO = AttributeName.of("org.apache.james.HELO");
    AttributeName SSL_PROTOCOL = AttributeName.of("org.apache.james.ssl.protocol");
    AttributeName SSL_CIPHER = AttributeName.of("org.apache.james.ssl.cipher");
    AttributeName SMTP_SESSION_ID = AttributeName.of("org.apache.james.SMTPSessionID");
    AttributeName MAILET_ERROR = AttributeName.of("org.apache.james.MailetError");
    Attribute SENT_BY_MAILET_ATTRIBUTE = Attribute.convertToAttribute("org.apache.james.SentByMailet", true);

    @Deprecated
    String SMTP_AUTH_USER_ATTRIBUTE_NAME = SMTP_AUTH_USER.asString();

    @Deprecated
    String MAILET_ERROR_ATTRIBUTE_NAME = MAILET_ERROR.asString();

    @Deprecated
    String SENT_BY_MAILET = SENT_BY_MAILET_ATTRIBUTE.getName().asString();

    /**
     * Returns the name of this message.
     * 
     * @return the message name
     * @since Mailet API v2.3
     */
    String getName();
    
    /**
     * Set the name of this message.
     * 
     * @param newName the new message name
     * @since Mailet API v2.3
     */
    void setName(String newName);
    
    /**
     * Returns the MimeMessage stored in this message.
     *
     * @return the MimeMessage that this Mail object wraps
     * @throws MessagingException when an error occurs while retrieving the message
     */
    MimeMessage getMessage() throws MessagingException;
    
    /**
     * Returns the message recipients as a Collection of MailAddress objects,
     * as specified by the SMTP "RCPT TO" command, or internally defined.
     *
     * @return a Collection of MailAddress objects that are recipients of this message
     */
    Collection<MailAddress> getRecipients();

    /**
     * Sets the message recipients as a Collection of MailAddress objects.
     * 
     * @param recipients the message recipients as a Collection of MailAddress Objects
     * @since Mailet API v2.4
     */
    void setRecipients(Collection<MailAddress> recipients);
    
    /**
     * Returns the sender of the message, as specified by the SMTP "MAIL FROM" command,
     * or internally defined.
     *
     * Note that SMTP null sender ( "&lt;&gt;" ) needs to be implicitly handled by the caller
     * in the form of 'null' or {@link MailAddress#nullSender()}.
     * Replacement method adds type safety on this operation.
     *
     * @return the sender of this message
     * @deprecated see {@link #getMaybeSender()}
     */
    @Deprecated
    MailAddress getSender();

    /**
     * Returns the sender of the message, as specified by the SMTP "MAIL FROM" command,
     * or internally defined.
     *
     * 'null' or {@link MailAddress#nullSender()} are handled with {@link MaybeSender#nullSender()}.
     *
     * @since Mailet API v3.2.0
     * @return the sender of this message wrapped in an optional
     */
    default MaybeSender getMaybeSender() {
        return MaybeSender.of(getSender());
    }

    /**
     * Returns if this message has a sender.
     *
     * {@link MaybeSender#nullSender()} will be considered as no sender.
     *
     * @since Mailet API v3.2.0
     */
    default boolean hasSender() {
        return !getMaybeSender().isNullSender();
    }

    /**
     * Returns a duplicate copy of this email.
     * Implementation can affect a variation of the initial mail name property.
     *
     * @since Mailet API v3.2.0
     * @return A copy of this email
     */
    Mail duplicate() throws MessagingException;
    
    /**
     * Returns the current state of the message, such as GHOST, ERROR or DEFAULT.
     *
     * @return the state of this message
     */
    String getState();
    
    /**
     * Returns the host name of the remote server that sent this message.
     *
     * @return the host name of the remote server that sent this message
     */
    String getRemoteHost();
    
    /**
     * Returns the IP address of the remote server that sent this message.
     *
     * @return the IP address of the remote server that sent this message
     */
    String getRemoteAddr();
    
    /**
     * The error message, if any, associated with this message.
     *
     * @return the error message associated with this message, or null
     */
    String getErrorMessage();
    
    /**
     * Sets the error message associated with this message.
     *
     * @param msg the error message
     */
    void setErrorMessage(String msg);
    
    /**
     * Sets the MimeMessage wrapped by this Mail instance.
     *
     * @param message the new message that this Mail instance will wrap
     */
    void setMessage(MimeMessage message) throws MessagingException;
    
    /**
     * Sets the state of this message.
     *
     * @param state the new state of this message
     */
    void setState(String state);

    /**
     * Get the stream of all attributes
     */
    Stream<Attribute> attributes();

    /**
     * Returns the value of the named Mail instance attribute,
     * or null if the attribute does not exist.
     *
     * @param name the attribute name
     * @return the attribute value, or null if the attribute does not exist
     * @since Mailet API v2.1
     * @deprecated see {@link #getAttribute(AttributeName)}
     */
    @Deprecated
    Serializable getAttribute(String name);

    /**
     * Returns the attribute corresponding to an attribute name
     *
     * @since Mailet API v3.2
     */
    Optional<Attribute> getAttribute(AttributeName name);

    /**
     * Returns an Iterator over the names of all attributes which are set
     * in this Mail instance.
     * <p>
     * The {@link #getAttribute} method can be called to
     * retrieve an attribute's value given its name.
     *
     * @return an Iterator (of Strings) over all attribute names
     * @since Mailet API v2.1
     * @deprecated see {@link #attributeNames()}
     */
    @Deprecated
    Iterator<String> getAttributeNames();

    /**
     * Returns a Stream over the names of all attributes which are set
     * in this Mail instance.
     * <p>
     * The {@link #getAttribute} method can be called to
     * retrieve an attribute's value given its name.
     *
     * @since Mailet API v3.2
     */
    Stream<AttributeName> attributeNames();

    /**
     * Returns whether this Mail instance has any attributes set.
     * 
     * @return true if this Mail instance has any attributes set, false if not
     * @since Mailet API v2.1
     */
    boolean hasAttributes();
    
    /**
     * Removes the attribute with the given name from this Mail instance.
     * 
     * @param name the name of the attribute to be removed
     * @return the value of the removed attribute, or null
     *      if there was no such attribute (or if the attribute existed
     *      and its value was null)
     * @since Mailet API v2.1
     * @deprecated see {@link #removeAttribute(AttributeName)
     */
    @Deprecated
    Serializable removeAttribute(String name);

    /**
     * Removes the attribute with the given attribute name from this Mail instance.
     * 
     * @return the removed attribute, or null
     *      if there was no such attribute (or if the attribute existed
     *      and its value was null)
     * @since Mailet API v3.2
     */
    Optional<Attribute> removeAttribute(AttributeName attributeName);
    
    /**
     * Removes all attributes associated with this Mail instance. 
     * @since Mailet API v2.1
     **/
    void removeAllAttributes();
    
    /**
     * Associates an attribute with the given name and value with this Mail instance.
     * If an attribute with the given name already exists, it is replaced, and the
     * previous value is returned.
     * <p>
     * Conventionally, attribute names should follow the namespacing guidelines
     * for Java packages.
     * The Mailet API specification reserves names matching
     * <i>org.apache.james.*</i> and <i>org.apache.mailet.*</i>.
     *
     * @param name the attribute name
     * @param object the attribute value
     * @return the value of the previously existing attribute with the same name,
     *      or null if there was no such attribute (or if the attribute existed
     *      and its value was null)
     * @since Mailet API v2.1
     * @deprecated see {@link #setAttribute(Attribute)
     */
    @Deprecated
    Serializable setAttribute(String name, Serializable object);

    /**
     * Associates an attribute with the given name and value with this Mail instance.
     * If an attribute with a given name already exists, it is replaced, and the
     * previous value is returned.
     * <p>
     * Conventionally, attribute names should follow the namespacing guidelines
     * for Java packages.
     * The Mailet API specification reserves names matching
     * <i>org.apache.james.*</i> and <i>org.apache.mailet.*</i>.
     *
     * @return the previously existing attribute with the same name,
     *      or null if there was no such attribute (or if the attribute existed
     *      and its value was null)
     * @since Mailet API v3.2
     */
    Optional<Attribute> setAttribute(Attribute attribute);

    /**
     * Adds a header (and its specific values) for a recipient.
     * This header will be stored only for this recipient at delivery time.
     * <p>
     * Note that the headers must contain only US-ASCII characters, so a header that
     * contains non US-ASCII characters must have been encoded by the
     * caller as per the rules of RFC 2047.
     *
     * @param header the header to add
     * @param recipient the recipient for which the header is added
     */
    void addSpecificHeaderForRecipient(Header header, MailAddress recipient);

    /** 
     * Get the currently stored association between recipients and
     * specific headers.
     *
     * @return the recipient-specific headers
     */
    PerRecipientHeaders getPerRecipientSpecificHeaders();

    /**
     * Returns the message size (including headers).
     * <p>
     * This is intended as a guide suitable for processing heuristics, and not
     * a precise indication of the number of outgoing bytes that would be produced
     * were the email to be encoded for transport.
     * In cases where an exact value is not readily available or is difficult to
     * determine (for example, when the fully transfer encoded message is not available)
     * a suitable estimate may be returned.
     * 
     * @return the message size
     * @throws MessagingException when the size cannot be retrieved
     * @since Mailet API v2.3
     */
    long getMessageSize() throws MessagingException;

    /**
     * Returns the time at which this Mail was last updated.
     *
     * @return the time at which this Mail was last updated
     * @since Mailet API v2.3
     */
    Date getLastUpdated();
    
    /**
     * Sets the time at which this Mail was last updated.
     *
     * @param lastUpdated the time at which this Mail was last modified
     * @since Mailet API v2.3
     */
    void setLastUpdated(Date lastUpdated);

    /**
     * Returns a map of AttribeName Attribute for the currently registered attributes
     *
     * @since Mailet API v3.2
     */
    default Map<AttributeName, Attribute> attributesMap() {
        return attributeNames()
            .map(name -> getAttribute(name).map(attribute -> Pair.of(name, attribute)))
            .flatMap(Optional::stream)
            .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue));
    }

    default Optional<DsnParameters> dsnParameters() {
        return DsnParameters.fromAttributeValue(DsnParameters.DsnAttributeValues.extract(attributesMap()));
    }

    default void setDsnParameters(DsnParameters dsnParameters) {
        DsnParameters.DsnAttributeValues.forEachDsnAttributeName(this::removeAttribute);
        dsnParameters.toAttributes()
            .asAttributes()
            .forEach(this::setAttribute);
    }
}
