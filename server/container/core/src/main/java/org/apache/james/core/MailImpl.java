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

package org.apache.james.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.ParseException;

import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.PerRecipientHeaders.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Wraps a MimeMessage adding routing information (from SMTP) and some simple
 * API enhancements.
 * </p>
 * <p>
 * From James version > 2.2.0a8 "mail attributes" have been added. Backward and
 * forward compatibility is supported:
 * <ul>
 * <li>messages stored in file repositories <i>without</i> attributes by James
 * version <= 2.2.0a8 will be processed by later versions as having an empty
 * attributes hashmap;</li>
 * <li>messages stored in file repositories <i>with</i> attributes by James
 * version > 2.2.0a8 will be processed by previous versions, ignoring the
 * attributes.</li>
 * </ul>
 * </p>
 */
public class MailImpl implements Disposable, Mail {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailImpl.class);

    /**
     * We hardcode the serialVersionUID so that from James 1.2 on, MailImpl will
     * be deserializable (so your mail doesn't get lost)
     */
    public static final long serialVersionUID = -4289663364703986260L;
    /**
     * The error message, if any, associated with this mail.
     */
    private String errorMessage;
    /**
     * The state of this mail, which determines how it is processed.
     */
    private String state;
    /**
     * The MimeMessage that holds the mail data.
     */
    private MimeMessage message;
    /**
     * The sender of this mail.
     */
    private MailAddress sender;
    /**
     * The collection of recipients to whom this mail was sent.
     */
    private Collection<MailAddress> recipients;
    /**
     * The identifier for this mail message
     */
    private String name;
    /**
     * The remote host from which this mail was sent.
     */
    private String remoteHost = "localhost";
    /**
     * The remote address from which this mail was sent.
     */
    private String remoteAddr = "127.0.0.1";
    /**
     * The last time this message was updated.
     */
    private Date lastUpdated = new Date();
    /**
     * Attributes added to this MailImpl instance
     */
    private Map<String, Object> attributes;
    /**
     * Specific headers for some recipients
     * These headers will be added at delivery time
     */
    private PerRecipientHeaders perRecipientSpecificHeaders;

    /**
     * A constructor that creates a new, uninitialized MailImpl
     */
    public MailImpl() {
        setState(Mail.DEFAULT);
        attributes = new HashMap<>();
        perRecipientSpecificHeaders = new PerRecipientHeaders();
    }

    /**
     * A constructor that creates a MailImpl with the specified name, sender,
     * and recipients.
     *
     * @param name       the name of the MailImpl
     * @param sender     the sender for this MailImpl
     * @param recipients the collection of recipients of this MailImpl
     */
    public MailImpl(String name, MailAddress sender, Collection<MailAddress> recipients) {
        this();
        this.name = name;
        this.sender = sender;
        this.recipients = null;

        // Copy the recipient list
        if (recipients != null) {
            this.recipients = new ArrayList<>();
            this.recipients.addAll(recipients);
        }
    }

    /**
     * Create a copy of the input mail and assign it a new name
     *
     * @param mail original mail
     * @throws MessagingException when the message is not clonable
     */
    public MailImpl(Mail mail) throws MessagingException {
        this(mail, newName(mail));
    }

    /**
     * @param mail
     * @param newName
     * @throws MessagingException
     */
    @SuppressWarnings("unchecked")
    public MailImpl(Mail mail, String newName) throws MessagingException {
        this(newName, mail.getSender(), mail.getRecipients(), mail.getMessage());
        setRemoteHost(mail.getRemoteHost());
        setRemoteAddr(mail.getRemoteAddr());
        setLastUpdated(mail.getLastUpdated());
        try {
            if (mail instanceof MailImpl) {
                setAttributesRaw((HashMap<String, Object>) cloneSerializableObject(((MailImpl) mail).getAttributesRaw()));
            } else {
                HashMap<String, Object> attribs = new HashMap<>();
                for (Iterator<String> i = mail.getAttributeNames(); i.hasNext(); ) {
                    String hashKey = i.next();
                    attribs.put(hashKey, cloneSerializableObject(mail.getAttribute(hashKey)));
                }
                setAttributesRaw(attribs);
            }
        } catch (IOException e) {
            LOGGER.error("Error while deserializing attributes", e);
            setAttributesRaw(new HashMap<>());
        } catch (ClassNotFoundException e) {
            LOGGER.error("Error while deserializing attributes", e);
            setAttributesRaw(new HashMap<>());
        }
    }

    /**
     * A constructor that creates a MailImpl with the specified name, sender,
     * recipients, and message data.
     *
     * @param name       the name of the MailImpl
     * @param sender     the sender for this MailImpl
     * @param recipients the collection of recipients of this MailImpl
     * @param messageIn  a stream containing the message source
     */
    public MailImpl(String name, MailAddress sender, Collection<MailAddress> recipients, InputStream messageIn) throws MessagingException {
        this(name, sender, recipients);
        MimeMessageSource source = new MimeMessageInputStreamSource(name, messageIn);
        // if MimeMessageCopyOnWriteProxy throws an error in the constructor we
        // have to manually care disposing our source.
        try {
            this.setMessage(new MimeMessageCopyOnWriteProxy(source));
        } catch (MessagingException e) {
            LifecycleUtil.dispose(source);
            throw e;
        }
    }

    /**
     * A constructor that creates a MailImpl with the specified name, sender,
     * recipients, and MimeMessage.
     *
     * @param name       the name of the MailImpl
     * @param sender     the sender for this MailImpl
     * @param recipients the collection of recipients of this MailImpl
     * @param message    the MimeMessage associated with this MailImpl
     */
    public MailImpl(String name, MailAddress sender, Collection<MailAddress> recipients, MimeMessage message) {
        this(name, sender, recipients);
        this.setMessage(new MimeMessageCopyOnWriteProxy(message));
    }

    /**
     * Duplicate the MailImpl.
     *
     * @return a MailImpl that is a duplicate of this one
     */
    public Mail duplicate() {
        return duplicate(name);
    }

    /**
     * Duplicate the MailImpl, replacing the mail name with the one passed in as
     * an argument.
     *
     * @param newName the name for the duplicated mail
     * @return a MailImpl that is a duplicate of this one with a different name
     */
    public Mail duplicate(String newName) {
        try {
            return new MailImpl(this, newName);
        } catch (MessagingException me) {
            // Ignored. Return null in the case of an error.
        }
        return null;
    }

    /**
     * Get the error message associated with this MailImpl.
     *
     * @return the error message associated with this MailImpl
     */
    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Get the MimeMessage associated with this MailImpl.
     *
     * @return the MimeMessage associated with this MailImpl
     */
    @Override
    public MimeMessage getMessage() throws MessagingException {
        return message;
    }

    /**
     * Set the name of this MailImpl.
     *
     * @param name the name of this MailImpl
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the name of this MailImpl.
     *
     * @return the name of this MailImpl
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Get the recipients of this MailImpl.
     *
     * @return the recipients of this MailImpl
     */
    @Override
    public Collection<MailAddress> getRecipients() {
        return recipients;
    }

    /**
     * Get the sender of this MailImpl.
     *
     * @return the sender of this MailImpl
     */
    @Override
    public MailAddress getSender() {
        return sender;
    }

    /**
     * Get the state of this MailImpl.
     *
     * @return the state of this MailImpl
     */
    @Override
    public String getState() {
        return state;
    }

    /**
     * Get the remote host associated with this MailImpl.
     *
     * @return the remote host associated with this MailImpl
     */
    @Override
    public String getRemoteHost() {
        return remoteHost;
    }

    /**
     * Get the remote address associated with this MailImpl.
     *
     * @return the remote address associated with this MailImpl
     */
    @Override
    public String getRemoteAddr() {
        return remoteAddr;
    }

    /**
     * Get the last updated time for this MailImpl.
     *
     * @return the last updated time for this MailImpl
     */
    @Override
    public Date getLastUpdated() {
        return lastUpdated;
    }

    /**
     * <p>
     * Return the size of the message including its headers.
     * MimeMessage.getSize() method only returns the size of the message body.
     * </p>
     * <p/>
     * <p>
     * Note: this size is not guaranteed to be accurate - see Sun's
     * documentation of MimeMessage.getSize().
     * </p>
     *
     * @return approximate size of full message including headers.
     * @throws MessagingException if a problem occurs while computing the message size
     */
    @Override
    public long getMessageSize() throws MessagingException {
        return MimeMessageUtil.getMessageSize(message);
    }

    /**
     * Set the error message associated with this MailImpl.
     *
     * @param msg the new error message associated with this MailImpl
     */
    @Override
    public void setErrorMessage(String msg) {
        this.errorMessage = msg;
    }

    /**
     * Set the MimeMessage associated with this MailImpl.
     *
     * @param message the new MimeMessage associated with this MailImpl
     */
    @Override
    public void setMessage(MimeMessage message) {

        // TODO: We should use the MimeMessageCopyOnWriteProxy
        // everytime we set the MimeMessage. We should
        // investigate if we should wrap it here

        if (this.message != message) {
            // If a setMessage is called on a Mail that already have a message
            // (discouraged) we have to make sure that the message we remove is
            // correctly unreferenced and disposed, otherwise it will keep locks
            if (this.message != null) {
                LifecycleUtil.dispose(this.message);
            }
            this.message = message;
        }
    }

    /**
     * Set the recipients for this MailImpl.
     *
     * @param recipients the recipients for this MailImpl
     */
    @Override
    public void setRecipients(Collection<MailAddress> recipients) {
        this.recipients = recipients;
    }

    /**
     * Set the sender of this MailImpl.
     *
     * @param sender the sender of this MailImpl
     */
    public void setSender(MailAddress sender) {
        this.sender = sender;
    }

    /**
     * Set the state of this MailImpl.
     *
     * @param state the state of this MailImpl
     */
    public void setState(String state) {
        this.state = state;
    }

    /**
     * Set the remote address associated with this MailImpl.
     *
     * @param remoteHost the new remote host associated with this MailImpl
     */
    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    /**
     * Set the remote address associated with this MailImpl.
     *
     * @param remoteAddr the new remote address associated with this MailImpl
     */
    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    /**
     * Set the date this mail was last updated.
     *
     * @param lastUpdated the date the mail was last updated
     */
    public void setLastUpdated(Date lastUpdated) {
        // Make a defensive copy to ensure that the date
        // doesn't get changed external to the class
        if (lastUpdated != null) {
            lastUpdated = new Date(lastUpdated.getTime());
        }
        this.lastUpdated = lastUpdated;
    }

    /**
     * Writes the message out to an OutputStream.
     *
     * @param out the OutputStream to which to write the content
     * @throws MessagingException if the MimeMessage is not set for this MailImpl
     * @throws IOException        if an error occurs while reading or writing from the stream
     */
    public void writeMessageTo(OutputStream out) throws IOException, MessagingException {
        if (message != null) {
            message.writeTo(out);
        } else {
            throw new MessagingException("No message set for this MailImpl.");
        }
    }

    // Serializable Methods
    // TODO: These need some work. Currently very tightly coupled to
    // the internal representation.

    /**
     * Read the MailImpl from an <code>ObjectInputStream</code>.
     *
     * @param in the ObjectInputStream from which the object is read
     * @throws IOException            if an error occurs while reading from the stream
     * @throws ClassNotFoundException ?
     * @throws ClassCastException     if the serialized objects are not of the appropriate type
     */
    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            Object obj = in.readObject();
            if (obj == null) {
                sender = null;
            } else if (obj instanceof String) {
                sender = new MailAddress((String) obj);
            } else if (obj instanceof MailAddress) {
                sender = (MailAddress) obj;
            }
        } catch (ParseException pe) {
            throw new IOException("Error parsing sender address: " + pe.getMessage());
        }
        recipients = (Collection<MailAddress>) in.readObject();
        state = (String) in.readObject();
        errorMessage = (String) in.readObject();
        name = (String) in.readObject();
        remoteHost = (String) in.readObject();
        remoteAddr = (String) in.readObject();
        setLastUpdated((Date) in.readObject());
        // the following is under try/catch to be backwards compatible
        // with messages created with James version <= 2.2.0a8
        try {
            attributes = (HashMap<String, Object>) in.readObject();
        } catch (OptionalDataException ode) {
            if (ode.eof) {
                attributes = new HashMap<>();
            } else {
                throw ode;
            }
        }
    }

    /**
     * Write the MailImpl to an <code>ObjectOutputStream</code>.
     *
     * @param out the ObjectOutputStream to which the object is written
     * @throws IOException if an error occurs while writing to the stream
     */
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeObject(sender);
        out.writeObject(recipients);
        out.writeObject(state);
        out.writeObject(errorMessage);
        out.writeObject(name);
        out.writeObject(remoteHost);
        out.writeObject(remoteAddr);
        out.writeObject(lastUpdated);
        out.writeObject(attributes);
    }

    @Override
    public void dispose() {
        LifecycleUtil.dispose(message);
        message = null;
    }

    /**
     * <p>
     * This method is necessary, when Mail repositories needs to deal explicitly
     * with storing Mail attributes as a Serializable
     * </p>
     * <p>
     * <strong>Note</strong>: This method is not exposed in the Mail interface,
     * it is for internal use by James only.
     * </p>
     *
     * @return Serializable of the entire attributes collection
     * @since 2.2.0
     */
    public Map<String, Object> getAttributesRaw() {
        return attributes;
    }

    /**
     * <p>
     * This method is necessary, when Mail repositories needs to deal explicitly
     * with retriving Mail attributes as a Serializable
     * </p>
     * <p>
     * <strong>Note</strong>: This method is not exposed in the Mail interface,
     * it is for internal use by James only.
     * </p>
     *
     * @param attr Serializable of the entire attributes collection
     * @since 2.2.0
     */
    public void setAttributesRaw(HashMap<String, Object> attr) {
        this.attributes = (attr == null) ? new HashMap<>() : attr;
    }

    @Override
    public Serializable getAttribute(String key) {
        return (Serializable) attributes.get(key);
    }

    @Override
    public Serializable setAttribute(String key, Serializable object) {
        return (Serializable) attributes.put(key, object);
    }

    @Override
    public Serializable removeAttribute(String key) {
        return (Serializable) attributes.remove(key);
    }

    @Override
    public void removeAllAttributes() {
        attributes.clear();
    }

    @Override
    public Iterator<String> getAttributeNames() {
        return attributes.keySet().iterator();
    }

    @Override
    public boolean hasAttributes() {
        return !attributes.isEmpty();
    }

    /**
     * This methods provide cloning for serializable objects. Mail Attributes
     * are Serializable but not Clonable so we need a deep copy
     *
     * @param o Object to be cloned
     * @return the cloned Object
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private static Object cloneSerializableObject(Object o) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(b);
        out.writeObject(o);
        out.flush();
        out.close();
        ByteArrayInputStream bi = new ByteArrayInputStream(b.toByteArray());
        ObjectInputStream in = new ObjectInputStream(bi);
        return in.readObject();
    }

    private static final java.util.Random random = new java.util.Random(); // Used
    // to
    // generate
    // new
    // mail
    // names

    /**
     * Create a unique new primary key name for the given MailObject.
     *
     * @param mail the mail to use as the basis for the new mail name
     * @return a new name
     */
    public static String newName(Mail mail) throws MessagingException {
        String oldName = mail.getName();

        // Checking if the original mail name is too long, perhaps because of a
        // loop caused by a configuration error.
        // it could cause a "null pointer exception" in AvalonMailRepository
        // much
        // harder to understand.
        if (oldName.length() > 76) {
            int count = 0;
            int index = 0;
            while ((index = oldName.indexOf('!', index + 1)) >= 0) {
                count++;
            }
            // It looks like a configuration loop. It's better to stop.
            if (count > 7) {
                throw new MessagingException("Unable to create a new message name: too long." + " Possible loop in config.xml.");
            } else {
                oldName = oldName.substring(0, 76);
            }
        }

        return oldName + "-!" + random.nextInt(1048576);
    }

    /**
     * Generate a new identifier/name for a mail being processed by this server.
     *
     * @return the new identifier
     */
    public static String getId() {
        return "Mail" + System.currentTimeMillis() + "-" + UUID.randomUUID();
    }

    @Override
    public PerRecipientHeaders getPerRecipientSpecificHeaders() {
        return perRecipientSpecificHeaders;
    }

    @Override
    public void addSpecificHeaderForRecipient(Header header, MailAddress recipient) {
        perRecipientSpecificHeaders.addHeaderForRecipient(header, recipient);
    }
}
