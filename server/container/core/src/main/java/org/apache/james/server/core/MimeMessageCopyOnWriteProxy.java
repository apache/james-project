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
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

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

import com.google.common.base.Preconditions;

/**
 * This object wraps a "possibly shared" MimeMessage tracking copies and
 * automatically cloning it (if shared) when a write operation is invoked.
 */
public class MimeMessageCopyOnWriteProxy extends MimeMessage implements Disposable {
    @FunctionalInterface
    interface Read<T> {
        T read(MimeMessage message) throws MessagingException;
    }

    @FunctionalInterface
    interface ReadIO<T> {
        T read(MimeMessage message) throws MessagingException, IOException;
    }

    @FunctionalInterface
    interface Write {
        void write(MimeMessage message) throws MessagingException;
    }

    @FunctionalInterface
    interface WriteIO {
        void write(MimeMessage message) throws MessagingException, IOException;
    }

    /**
     * Used internally to track the reference count It is important that this is
     * static otherwise it will keep a reference to the parent object.
     */
    private static class MessageReferenceTracker {

        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        /**
         * reference counter
         */
        private volatile int referenceCount = 1;

        /**
         * The mime message in memory
         */
        private MimeMessage wrapped = null;

        private MessageReferenceTracker(MimeMessage ref) {
            wrapped = ref;
        }

        private void dispose() {
            ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
            writeLock.lock();
            try {
                referenceCount--;
                if (referenceCount <= 0) {
                    LifecycleUtil.dispose(wrapped);
                    wrapped = null;
                }
            } finally {
                writeLock.unlock();
            }
        }

        private void incrementReferences() {
            ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
            writeLock.lock();
            try {
                referenceCount++;
            } finally {
                writeLock.unlock();
            }
        }

        private <T> T wrapRead(Read<T> op) throws MessagingException {
            ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
            readLock.lock();
            try {
                Preconditions.checkState(referenceCount > 0, "Attempt to read a disposed message");
                return op.read(wrapped);
            } finally {
                readLock.unlock();
            }
        }

        private <T> T wrapReadIO(ReadIO<T> op) throws MessagingException, IOException {
            ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
            readLock.lock();
            try {
                Preconditions.checkState(referenceCount > 0, "Attempt to read a disposed message");
                return op.read(wrapped);
            } finally {
                readLock.unlock();
            }
        }

        private <T> T wrapReadNoException(Function<MimeMessage, T> op) {
            ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
            readLock.lock();
            try {
                Preconditions.checkState(referenceCount > 0, "Attempt to read a disposed message");
                return op.apply(wrapped);
            } finally {
                readLock.unlock();
            }
        }

        private MessageReferenceTracker wrapWrite(Write op) throws MessagingException {
            ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
            writeLock.lock();
            try {
                Preconditions.checkState(referenceCount > 0, "Attempt to write a disposed message");
                if (referenceCount > 1) {
                    referenceCount--;
                    MessageReferenceTracker newRef = new MessageReferenceTracker(new MimeMessageWrapper(wrapped));
                    newRef.wrapWrite(op);
                    return newRef;
                } else {
                    op.write(wrapped);
                    return this;
                }
            } finally {
                writeLock.unlock();
            }
        }

        private MessageReferenceTracker wrapWriteIO(WriteIO op) throws MessagingException, IOException {
            ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
            writeLock.lock();
            try {
                Preconditions.checkState(referenceCount > 0, "Attempt to write a disposed message");
                if (referenceCount > 1) {
                    referenceCount--;
                    MessageReferenceTracker newRef = new MessageReferenceTracker(new MimeMessageWrapper(wrapped));
                    newRef.wrapWriteIO(op);
                    return newRef;
                } else {
                    op.write(wrapped);
                    return this;
                }
            } finally {
                writeLock.unlock();
            }
        }

        private MimeMessage getWrapped() {
            return wrapped;
        }

        private MessageReferenceTracker newRef() {
            ReentrantReadWriteLock.WriteLock writeLock = this.lock.writeLock();
            writeLock.lock();
            try {
                if (referenceCount > 0) {
                    referenceCount++;
                    // Still valid
                    return this;
                }
                throw new IllegalStateException("Message was disposed so new refs cannot be obtained");
            } finally {
                writeLock.unlock();
            }
        }
    }

    /**
     * @return a MimeMessageCopyOnWriteProxy wrapping the message. We consider 'message' as an external reference and
     * will avoid mutate it, rather cloning the email.
     *
     * Please note however that modifying 'message' itself will not lead to a correct reference tracking management
     * unless it is a MimeMessageCopyOnWriteProxy
     */
    public static MimeMessageCopyOnWriteProxy fromMimeMessage(MimeMessage message) {
        return new MimeMessageCopyOnWriteProxy(message, true);
    }

    /**
     * @return a MimeMessageCopyOnWriteProxy wrapping the message. We do not consider 'message' as an external reference
     * and will mutate it, rather than cloning the email.
     *
     * Please note however that modifying 'message' itself will not lead to a correct reference tracking management
     * unless it is a MimeMessageCopyOnWriteProxy
     */
    public static MimeMessageCopyOnWriteProxy fromMimeMessageNoExternalReference(MimeMessage message) {
        return new MimeMessageCopyOnWriteProxy(message, false);
    }

    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    private MessageReferenceTracker refCount;

    private MimeMessageCopyOnWriteProxy(MimeMessage original, boolean originalIsAReference) {
        super(Session.getDefaultInstance(System.getProperties(), null));

        if (original instanceof MimeMessageCopyOnWriteProxy) {
            refCount = ((MimeMessageCopyOnWriteProxy) original).newRef();
        } else {
            refCount = new MessageReferenceTracker(original);
            if (originalIsAReference) {
                refCount.incrementReferences(); // We consider 'original' to be a reference too
            }
        }
    }

    public MimeMessageCopyOnWriteProxy(MimeMessageSource original) {
        this(new MimeMessageWrapper(original), false);
    }

    /**
     * Return wrapped mimeMessage
     * 
     * @return wrapped return the wrapped mimeMessage
     */
    public MimeMessage getWrappedMessage() {
        return refCount.getWrapped();
    }

    public MessageReferenceTracker newRef() {
        ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            return refCount.newRef();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void dispose() {
        ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            if (refCount != null) {
                refCount.dispose();
                refCount = null;
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void writeTo(OutputStream os) throws IOException, MessagingException {
        wrapWriteIO(message -> message.writeTo(os));
    }

    /**
     * Rewritten for optimization purposes
     */
    @Override
    public void writeTo(OutputStream os, String[] ignoreList) throws IOException, MessagingException {
        wrapWriteIO(message -> message.writeTo(os, ignoreList));
    }

    /*
     * Various reader methods
     */

    @Override
    public Address[] getFrom() throws MessagingException {
        return wrapRead(MimeMessage::getFrom);
    }

    public Optional<InputStream> getWrappedInputStream(boolean tryCast) throws MessagingException {
        return wrapRead(message -> {
            if (tryCast && message instanceof MimeMessageWrapper) {
                MimeMessageWrapper messageWrapper = (MimeMessageWrapper) message;
                return Optional.ofNullable(messageWrapper.getMessageInputStream());
            }
            return Optional.empty();
        });
    }

    @Override
    public Address[] getRecipients(Message.RecipientType type) throws MessagingException {
        return wrapRead(message -> message.getRecipients(type));
    }

    @Override
    public Address[] getAllRecipients() throws MessagingException {
        return wrapRead(MimeMessage::getFrom);
    }

    @Override
    public Address[] getReplyTo() throws MessagingException {
        return wrapRead(MimeMessage::getFrom);
    }

    @Override
    public String getSubject() throws MessagingException {
        return wrapRead(MimeMessage::getSubject);
    }

    @Override
    public Date getSentDate() throws MessagingException {
        return wrapRead(MimeMessage::getSentDate);
    }

    @Override
    public Date getReceivedDate() throws MessagingException {
        return wrapRead(MimeMessage::getReceivedDate);
    }

    @Override
    public int getSize() throws MessagingException {
        return wrapRead(MimeMessage::getSize);
    }

    @Override
    public int getLineCount() throws MessagingException {
        return wrapRead(MimeMessage::getLineCount);
    }

    @Override
    public String getContentType() throws MessagingException {
        return wrapRead(MimeMessage::getContentType);
    }

    @Override
    public boolean isMimeType(String mimeType) throws MessagingException {
        return wrapRead(message -> message.isMimeType(mimeType));
    }

    @Override
    public String getDisposition() throws MessagingException {
        return wrapRead(MimeMessage::getDisposition);
    }

    @Override
    public String getEncoding() throws MessagingException {
        return wrapRead(MimeMessage::getEncoding);
    }

    @Override
    public String getContentID() throws MessagingException {
        return wrapRead(MimeMessage::getContentID);
    }

    @Override
    public String getContentMD5() throws MessagingException {
        return wrapRead(MimeMessage::getContentMD5);
    }

    @Override
    public String getDescription() throws MessagingException {
        return wrapRead(MimeMessage::getDescription);
    }

    @Override
    public String[] getContentLanguage() throws MessagingException {
        return wrapRead(MimeMessage::getContentLanguage);
    }

    @Override
    public String getMessageID() throws MessagingException {
        return wrapRead(MimeMessage::getMessageID);
    }

    @Override
    public String getFileName() throws MessagingException {
        return wrapRead(MimeMessage::getFileName);
    }

    @Override
    public InputStream getInputStream() throws IOException, MessagingException {
        return wrapReadIO(MimeMessage::getInputStream);
    }

    @Override
    public DataHandler getDataHandler() throws MessagingException {
        return wrapRead(MimeMessage::getDataHandler);
    }

    @Override
    public Object getContent() throws IOException, MessagingException {
        return wrapReadIO(MimeMessage::getContent);
    }

    @Override
    public String[] getHeader(String name) throws MessagingException {
        return wrapRead(message -> message.getHeader(name));
    }

    @Override
    public String getHeader(String name, String delimiter) throws MessagingException {
        return wrapRead(message -> message.getHeader(name, delimiter));
    }

    @Override
    public Enumeration<Header> getAllHeaders() throws MessagingException {
        return wrapRead(MimeMessage::getAllHeaders);
    }

    @Override
    public Enumeration<Header> getMatchingHeaders(String[] names) throws MessagingException {
        return wrapRead(message -> message.getMatchingHeaders(names));
    }

    @Override
    public Enumeration<Header> getNonMatchingHeaders(String[] names) throws MessagingException {
        return wrapRead(message -> message.getNonMatchingHeaders(names));
    }

    @Override
    public Enumeration<String> getAllHeaderLines() throws MessagingException {
        return wrapRead(MimeMessage::getAllHeaderLines);
    }

    @Override
    public Enumeration<String> getMatchingHeaderLines(String[] names) throws MessagingException {
        return wrapRead(message -> message.getMatchingHeaderLines(names));
    }

    @Override
    public Enumeration<String> getNonMatchingHeaderLines(String[] names) throws MessagingException {
        return wrapRead(message -> message.getNonMatchingHeaderLines(names));
    }

    @Override
    public Flags getFlags() throws MessagingException {
        return wrapRead(MimeMessage::getFlags);
    }

    @Override
    public boolean isSet(Flags.Flag flag) throws MessagingException {
        return wrapRead(message -> message.isSet(flag));
    }

    @Override
    public Address getSender() throws MessagingException {
        return wrapRead(MimeMessage::getSender);
    }

    @Override
    public boolean match(SearchTerm arg0) throws MessagingException {
        return wrapRead(message -> message.match(arg0));
    }

    @Override
    public InputStream getRawInputStream() throws MessagingException {
        return wrapRead(MimeMessage::getRawInputStream);
    }

    @Override
    public Folder getFolder() {
        return wrapReadNoException(MimeMessage::getFolder);
    }

    @Override
    public int getMessageNumber() {
        return wrapReadNoException(MimeMessage::getMessageNumber);
    }

    @Override
    public boolean isExpunged() {
        return wrapReadNoException(MimeMessage::isExpunged);
    }

    @Override
    public boolean equals(Object arg0) {
        return wrapReadNoException(arg0::equals);
    }

    @Override
    public int hashCode() {
        return wrapReadNoException(MimeMessage::hashCode);
    }

    @Override
    public String toString() {
        return wrapReadNoException(MimeMessage::toString);
    }

    @Override
    public void setFrom(Address address) throws MessagingException {
        wrapWrite(message -> message.setFrom(address));
    }

    @Override
    public void setFrom() throws MessagingException {
        wrapWrite(MimeMessage::setFrom);
    }

    @Override
    public void addFrom(Address[] addresses) throws MessagingException {
        wrapWrite(message -> message.addFrom(addresses));
    }

    @Override
    public void setRecipients(Message.RecipientType type, Address[] addresses) throws MessagingException {
        wrapWrite(message -> message.setRecipients(type, addresses));
    }

    @Override
    public void addRecipients(Message.RecipientType type, Address[] addresses) throws MessagingException {
        wrapWrite(message -> message.addRecipients(type, addresses));
    }

    @Override
    public void setReplyTo(Address[] addresses) throws MessagingException {
        wrapWrite(message -> message.setReplyTo(addresses));
    }

    @Override
    public void setSubject(String subject) throws MessagingException {
        wrapWrite(message -> message.setSubject(subject));
    }

    @Override
    public void setSubject(String subject, String charset) throws MessagingException {
        wrapWrite(message -> message.setSubject(subject, charset));
    }

    @Override
    public void setSentDate(Date d) throws MessagingException {
        wrapWrite(message -> message.setSentDate(d));
    }

    @Override
    public void setDisposition(String disposition) throws MessagingException {
        wrapWrite(message -> message.setDisposition(disposition));
    }

    @Override
    public void setContentID(String cid) throws MessagingException {
        wrapWrite(message -> message.setContentID(cid));
    }

    @Override
    public void setContentMD5(String md5) throws MessagingException {
        wrapWrite(message -> message.setContentMD5(md5));
    }

    @Override
    public void setDescription(String description) throws MessagingException {
        wrapWrite(message -> message.setDescription(description));
    }

    @Override
    public void setDescription(String description, String charset) throws MessagingException {
        wrapWrite(message -> message.setDescription(description, charset));
    }

    @Override
    public void setContentLanguage(String[] languages) throws MessagingException {
        wrapWrite(message -> message.setContentLanguage(languages));
    }

    @Override
    public void setFileName(String filename) throws MessagingException {
        wrapWrite(message -> message.setFileName(filename));
    }

    @Override
    public void setDataHandler(DataHandler dh) throws MessagingException {
        wrapWrite(message -> message.setDataHandler(dh));
    }

    @Override
    public void setContent(Object o, String type) throws MessagingException {
        wrapWrite(message -> message.setContent(o, type));
    }

    @Override
    public void setText(String text) throws MessagingException {
        wrapWrite(message -> message.setText(text));
    }

    @Override
    public void setText(String text, String charset) throws MessagingException {
        wrapWrite(message -> message.setText(text, charset));
    }

    @Override
    public void setContent(Multipart mp) throws MessagingException {
        wrapWrite(message -> message.setContent(mp));
    }

    /**
     * This does not need a writable message
     */
    @Override
    public Message reply(boolean replyToAll) throws MessagingException {
        return wrapRead(message -> message.reply(replyToAll));
    }

    @Override
    public void setHeader(String name, String value) throws MessagingException {
        wrapWrite(message -> message.setHeader(name, value));
    }

    @Override
    public void addHeader(String name, String value) throws MessagingException {
        wrapWrite(message -> message.addHeader(name, value));
    }

    @Override
    public void removeHeader(String name) throws MessagingException {
        wrapWrite(message -> message.removeHeader(name));
    }

    @Override
    public void addHeaderLine(String line) throws MessagingException {
        wrapWrite(message -> message.addHeaderLine(line));
    }

    @Override
    public void setFlags(Flags flag, boolean set) throws MessagingException {
        wrapWrite(message -> message.setFlags(flags, set));
    }

    @Override
    public void saveChanges() throws MessagingException {
        wrapWrite(MimeMessage::saveChanges);
    }

    /*
     * Since JavaMail 1.2
     */

    @Override
    public void addRecipients(Message.RecipientType type, String addresses) throws MessagingException {
        wrapWrite(message -> message.addRecipients(type, addresses));
    }

    @Override
    public void setRecipients(Message.RecipientType type, String addresses) throws MessagingException {
        wrapWrite(message -> message.setRecipients(type, addresses));
    }

    @Override
    public void setSender(Address arg0) throws MessagingException {
        wrapWrite(message -> message.setSender(arg0));
    }

    public void addRecipient(RecipientType arg0, Address arg1) throws MessagingException {
        wrapWrite(message -> message.addRecipient(arg0, arg1));
    }

    @Override
    public void setFlag(Flag arg0, boolean arg1) throws MessagingException {
        wrapWrite(message -> message.setFlag(arg0, arg1));
    }

    public long getMessageSize() throws MessagingException {
        return wrapRead(MimeMessageUtil::getMessageSize);
    }

    /**
     * Since javamail 1.4
     */
    @Override
    public void setText(String text, String charset, String subtype) throws MessagingException {
        wrapWrite(message -> message.setText(text, charset, subtype));
    }

    private void wrapWrite(Write op) throws MessagingException {
        ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            refCount = refCount.wrapWrite(op);
        } finally {
            writeLock.unlock();
        }
    }

    private void wrapWriteIO(WriteIO op) throws MessagingException, IOException {
        ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            refCount = refCount.wrapWriteIO(op);
        } finally {
            writeLock.unlock();
        }
    }

    private <T> T wrapRead(Read<T> op) throws MessagingException {
        ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            return refCount.wrapRead(op);
        } finally {
            readLock.unlock();
        }
    }

    private <T> T wrapReadIO(ReadIO<T> op) throws MessagingException, IOException {
        ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            return refCount.wrapReadIO(op);
        } finally {
            readLock.unlock();
        }
    }

    private <T> T wrapReadNoException(Function<MimeMessage, T> op) {
        ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            return refCount.wrapReadNoException(op);
        } finally {
            readLock.unlock();
        }
    }
}
