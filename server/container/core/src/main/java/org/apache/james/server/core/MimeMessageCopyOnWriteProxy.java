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

package org.apache.james.server.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;

import javax.activation.DataHandler;
import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.search.SearchTerm;

import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.lifecycle.api.LifecycleUtil;

/**
 * This object wraps a "possibly shared" MimeMessage tracking copies and
 * automatically cloning it (if shared) when a write operation is invoked.
 */
public class MimeMessageCopyOnWriteProxy extends MimeMessage implements Disposable {

    /**
     * Used internally to track the reference count It is important that this is
     * static otherwise it will keep a reference to the parent object.
     */
    protected static class MessageReferenceTracker {

        /**
         * reference counter
         */
        private int referenceCount = 1;

        /**
         * The mime message in memory
         */
        private MimeMessage wrapped = null;

        public MessageReferenceTracker(MimeMessage ref) {
            wrapped = ref;
        }

        protected synchronized void incrementReferenceCount() {
            referenceCount++;
        }

        protected synchronized void decrementReferenceCount() {
            referenceCount--;
            if (referenceCount <= 0) {
                LifecycleUtil.dispose(wrapped);
                wrapped = null;
            }
        }

        protected synchronized int getReferenceCount() {
            return referenceCount;
        }

        public synchronized MimeMessage getWrapped() {
            return wrapped;
        }

    }

    protected MessageReferenceTracker refCount;

    public MimeMessageCopyOnWriteProxy(MimeMessage original) {
        this(original, false);
    }

    public MimeMessageCopyOnWriteProxy(MimeMessageSource original) throws MessagingException {
        this(new MimeMessageWrapper(original), true);
    }

    /**
     * Private constructor providing an external reference counter.
     */
    private MimeMessageCopyOnWriteProxy(MimeMessage original, boolean writeable) {
        super(Session.getDefaultInstance(System.getProperties(), null));

        if (original instanceof MimeMessageCopyOnWriteProxy) {
            refCount = ((MimeMessageCopyOnWriteProxy) original).refCount;
        } else {
            refCount = new MessageReferenceTracker(original);
        }

        if (!writeable) {
            refCount.incrementReferenceCount();
        }
    }

    /**
     * Check the number of references over the MimeMessage and clone it if
     * needed before returning the reference
     * 
     * @throws MessagingException
     *             exception
     */
    protected synchronized MimeMessage getWrappedMessageForWriting() throws MessagingException {
        if (refCount.getReferenceCount() > 1) {
            refCount.decrementReferenceCount();
            refCount = new MessageReferenceTracker(new MimeMessageWrapper(refCount.getWrapped()));
        }
        return refCount.getWrapped();
    }

    /**
     * Return wrapped mimeMessage
     * 
     * @return wrapped return the wrapped mimeMessage
     */
    public synchronized MimeMessage getWrappedMessage() {
        return refCount.getWrapped();
    }

    @Override
    public synchronized void dispose() {
        if (refCount != null) {
            refCount.decrementReferenceCount();
            refCount = null;
        }
    }

    @Override
    public void writeTo(OutputStream os) throws IOException, MessagingException {
        getWrappedMessage().writeTo(os);
    }

    /**
     * Rewritten for optimization purposes
     */
    @Override
    public void writeTo(OutputStream os, String[] ignoreList) throws IOException, MessagingException {
        getWrappedMessage().writeTo(os, ignoreList);
    }

    /*
     * Various reader methods
     */

    @Override
    public Address[] getFrom() throws MessagingException {
        return getWrappedMessage().getFrom();
    }

    @Override
    public Address[] getRecipients(Message.RecipientType type) throws MessagingException {
        return getWrappedMessage().getRecipients(type);
    }

    @Override
    public Address[] getAllRecipients() throws MessagingException {
        return getWrappedMessage().getAllRecipients();
    }

    @Override
    public Address[] getReplyTo() throws MessagingException {
        return getWrappedMessage().getReplyTo();
    }

    @Override
    public String getSubject() throws MessagingException {
        return getWrappedMessage().getSubject();
    }

    @Override
    public Date getSentDate() throws MessagingException {
        return getWrappedMessage().getSentDate();
    }

    @Override
    public Date getReceivedDate() throws MessagingException {
        return getWrappedMessage().getReceivedDate();
    }

    @Override
    public int getSize() throws MessagingException {
        return getWrappedMessage().getSize();
    }

    @Override
    public int getLineCount() throws MessagingException {
        return getWrappedMessage().getLineCount();
    }

    @Override
    public String getContentType() throws MessagingException {
        return getWrappedMessage().getContentType();
    }

    @Override
    public boolean isMimeType(String mimeType) throws MessagingException {
        return getWrappedMessage().isMimeType(mimeType);
    }

    @Override
    public String getDisposition() throws MessagingException {
        return getWrappedMessage().getDisposition();
    }

    @Override
    public String getEncoding() throws MessagingException {
        return getWrappedMessage().getEncoding();
    }

    @Override
    public String getContentID() throws MessagingException {
        return getWrappedMessage().getContentID();
    }

    @Override
    public String getContentMD5() throws MessagingException {
        return getWrappedMessage().getContentMD5();
    }

    @Override
    public String getDescription() throws MessagingException {
        return getWrappedMessage().getDescription();
    }

    @Override
    public String[] getContentLanguage() throws MessagingException {
        return getWrappedMessage().getContentLanguage();
    }

    @Override
    public String getMessageID() throws MessagingException {
        return getWrappedMessage().getMessageID();
    }

    @Override
    public String getFileName() throws MessagingException {
        return getWrappedMessage().getFileName();
    }

    @Override
    public InputStream getInputStream() throws IOException, MessagingException {
        return getWrappedMessage().getInputStream();
    }

    @Override
    public DataHandler getDataHandler() throws MessagingException {
        return getWrappedMessage().getDataHandler();
    }

    @Override
    public Object getContent() throws IOException, MessagingException {
        return getWrappedMessage().getContent();
    }

    @Override
    public String[] getHeader(String name) throws MessagingException {
        return getWrappedMessage().getHeader(name);
    }

    @Override
    public String getHeader(String name, String delimiter) throws MessagingException {
        return getWrappedMessage().getHeader(name, delimiter);
    }

    @Override
    public Enumeration<Header> getAllHeaders() throws MessagingException {
        return getWrappedMessage().getAllHeaders();
    }

    @Override
    public Enumeration<Header> getMatchingHeaders(String[] names) throws MessagingException {
        return getWrappedMessage().getMatchingHeaders(names);
    }

    @Override
    public Enumeration<Header> getNonMatchingHeaders(String[] names) throws MessagingException {
        return getWrappedMessage().getNonMatchingHeaders(names);
    }

    @Override
    public Enumeration<String> getAllHeaderLines() throws MessagingException {
        return getWrappedMessage().getAllHeaderLines();
    }

    @Override
    public Enumeration<String> getMatchingHeaderLines(String[] names) throws MessagingException {
        return getWrappedMessage().getMatchingHeaderLines(names);
    }

    @Override
    public Enumeration<String> getNonMatchingHeaderLines(String[] names) throws MessagingException {
        return getWrappedMessage().getNonMatchingHeaderLines(names);
    }

    @Override
    public Flags getFlags() throws MessagingException {
        return getWrappedMessage().getFlags();
    }

    @Override
    public boolean isSet(Flags.Flag flag) throws MessagingException {
        return getWrappedMessage().isSet(flag);
    }

    @Override
    public Address getSender() throws MessagingException {
        return getWrappedMessage().getSender();
    }

    @Override
    public boolean match(SearchTerm arg0) throws MessagingException {
        return getWrappedMessage().match(arg0);
    }

    @Override
    public InputStream getRawInputStream() throws MessagingException {
        return getWrappedMessage().getRawInputStream();
    }

    @Override
    public Folder getFolder() {
        return getWrappedMessage().getFolder();
    }

    @Override
    public int getMessageNumber() {
        return getWrappedMessage().getMessageNumber();
    }

    @Override
    public boolean isExpunged() {
        return getWrappedMessage().isExpunged();
    }

    @Override
    public boolean equals(Object arg0) {
        return getWrappedMessage().equals(arg0);
    }

    @Override
    public int hashCode() {
        return getWrappedMessage().hashCode();
    }

    @Override
    public String toString() {
        return getWrappedMessage().toString();
    }

    @Override
    public void setFrom(Address address) throws MessagingException {
        getWrappedMessageForWriting().setFrom(address);
    }

    @Override
    public void setFrom() throws MessagingException {
        getWrappedMessageForWriting().setFrom();
    }

    @Override
    public void addFrom(Address[] addresses) throws MessagingException {
        getWrappedMessageForWriting().addFrom(addresses);
    }

    @Override
    public void setRecipients(Message.RecipientType type, Address[] addresses) throws MessagingException {
        getWrappedMessageForWriting().setRecipients(type, addresses);
    }

    @Override
    public void addRecipients(Message.RecipientType type, Address[] addresses) throws MessagingException {
        getWrappedMessageForWriting().addRecipients(type, addresses);
    }

    @Override
    public void setReplyTo(Address[] addresses) throws MessagingException {
        getWrappedMessageForWriting().setReplyTo(addresses);
    }

    @Override
    public void setSubject(String subject) throws MessagingException {
        getWrappedMessageForWriting().setSubject(subject);
    }

    @Override
    public void setSubject(String subject, String charset) throws MessagingException {
        getWrappedMessageForWriting().setSubject(subject, charset);
    }

    @Override
    public void setSentDate(Date d) throws MessagingException {
        getWrappedMessageForWriting().setSentDate(d);
    }

    @Override
    public void setDisposition(String disposition) throws MessagingException {
        getWrappedMessageForWriting().setDisposition(disposition);
    }

    @Override
    public void setContentID(String cid) throws MessagingException {
        getWrappedMessageForWriting().setContentID(cid);
    }

    @Override
    public void setContentMD5(String md5) throws MessagingException {
        getWrappedMessageForWriting().setContentMD5(md5);
    }

    @Override
    public void setDescription(String description) throws MessagingException {
        getWrappedMessageForWriting().setDescription(description);
    }

    @Override
    public void setDescription(String description, String charset) throws MessagingException {
        getWrappedMessageForWriting().setDescription(description, charset);
    }

    @Override
    public void setContentLanguage(String[] languages) throws MessagingException {
        getWrappedMessageForWriting().setContentLanguage(languages);
    }

    @Override
    public void setFileName(String filename) throws MessagingException {
        getWrappedMessageForWriting().setFileName(filename);
    }

    @Override
    public void setDataHandler(DataHandler dh) throws MessagingException {
        getWrappedMessageForWriting().setDataHandler(dh);
    }

    @Override
    public void setContent(Object o, String type) throws MessagingException {
        getWrappedMessageForWriting().setContent(o, type);
    }

    @Override
    public void setText(String text) throws MessagingException {
        getWrappedMessageForWriting().setText(text);
    }

    @Override
    public void setText(String text, String charset) throws MessagingException {
        getWrappedMessageForWriting().setText(text, charset);
    }

    @Override
    public void setContent(Multipart mp) throws MessagingException {
        getWrappedMessageForWriting().setContent(mp);
    }

    /**
     * This does not need a writable message
     */
    @Override
    public Message reply(boolean replyToAll) throws MessagingException {
        return getWrappedMessage().reply(replyToAll);
    }

    @Override
    public void setHeader(String name, String value) throws MessagingException {
        getWrappedMessageForWriting().setHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) throws MessagingException {
        getWrappedMessageForWriting().addHeader(name, value);
    }

    @Override
    public void removeHeader(String name) throws MessagingException {
        getWrappedMessageForWriting().removeHeader(name);
    }

    @Override
    public void addHeaderLine(String line) throws MessagingException {
        getWrappedMessageForWriting().addHeaderLine(line);
    }

    @Override
    public void setFlags(Flags flag, boolean set) throws MessagingException {
        getWrappedMessageForWriting().setFlags(flag, set);
    }

    @Override
    public void saveChanges() throws MessagingException {
        getWrappedMessageForWriting().saveChanges();
    }

    /*
     * Since JavaMail 1.2
     */

    @Override
    public void addRecipients(Message.RecipientType type, String addresses) throws MessagingException {
        getWrappedMessageForWriting().addRecipients(type, addresses);
    }

    @Override
    public void setRecipients(Message.RecipientType type, String addresses) throws MessagingException {
        getWrappedMessageForWriting().setRecipients(type, addresses);
    }

    @Override
    public void setSender(Address arg0) throws MessagingException {
        getWrappedMessageForWriting().setSender(arg0);
    }

    public void addRecipient(RecipientType arg0, Address arg1) throws MessagingException {
        getWrappedMessageForWriting().addRecipient(arg0, arg1);
    }

    @Override
    public void setFlag(Flag arg0, boolean arg1) throws MessagingException {
        getWrappedMessageForWriting().setFlag(arg0, arg1);
    }

    public long getMessageSize() throws MessagingException {
        return MimeMessageUtil.getMessageSize(getWrappedMessage());
    }

    /**
     * Since javamail 1.4
     */
    @Override
    public void setText(String text, String charset, String subtype) throws MessagingException {
        getWrappedMessage().setText(text, charset, subtype);
    }

}
