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
package org.apache.james.smtpserver.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

import javax.activation.DataHandler;
import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeMessage;
import javax.mail.search.SearchTerm;

import com.github.steveash.guavate.Guavate;

public class MockMimeMessage extends MimeMessage {

    private final List<Address> fromAddresses = new ArrayList<>();
    private Address senderAddress;
    private final List<Address> toRecepients = new ArrayList<>();
    private final List<Address> ccRecepients = new ArrayList<>();
    private final List<Address> bccRecepients = new ArrayList<>();
    private final List<Address> replyToAddresses = new ArrayList<>();
    private String subject;
    private int messageNumber;
    private boolean isExpunged;
    private Object content;
    private Date sentDate;
    private String[] contentLanguage;
    private String fileName;
    private DataHandler dataHandler;
    private final HashMap<String, String> contentHeaders = new HashMap<>();
    private final Flags setFlags = new Flags();
    private boolean doMatch;

    public MockMimeMessage() {
        super((Session) null);
    }

    public MockMimeMessage(int messageNumber) {
        super((Session) null);
        this.messageNumber = messageNumber;
    }

    public MockMimeMessage(MimeMessage mimeMessage) throws MessagingException {
        super(mimeMessage); // trivial implementation
    }

    @Override
    public Address[] getFrom() throws MessagingException {
        return fromAddresses.toArray(new Address[0]);
    }

    @Override
    public void setFrom(Address address) throws MessagingException {
        fromAddresses.clear();
        fromAddresses.add(address);
    }

    @Override
    public void setFrom() throws MessagingException {
        fromAddresses.clear();
        fromAddresses.add(InternetAddress.getLocalAddress(null));
    }

    @Override
    public void addFrom(Address[] addresses) throws MessagingException {
        fromAddresses.addAll(Arrays.asList(addresses));
    }

    @Override
    public Address getSender() throws MessagingException {
        return senderAddress;
    }

    @Override
    public void setSender(Address address) throws MessagingException {
        senderAddress = address;
    }

    @Override
    public Address[] getRecipients(Message.RecipientType recipientType) throws MessagingException {
        List<Address> recipientsList = getRecipientsList(recipientType);
        return recipientsList.toArray(new Address[0]);
    }

    private List<Address> getRecipientsList(Message.RecipientType recipientType) {
        if (Message.RecipientType.TO.equals(recipientType)) {
            return toRecepients;
        }
        if (Message.RecipientType.CC.equals(recipientType)) {
            return ccRecepients;
        }
        if (Message.RecipientType.BCC.equals(recipientType)) {
            return bccRecepients;
        }
        return null;
    }

    @Override
    public Address[] getAllRecipients() throws MessagingException {
        List<Address> allRecipients = new ArrayList<>();
        allRecipients.addAll(toRecepients);
        allRecipients.addAll(ccRecepients);
        allRecipients.addAll(bccRecepients);
        return allRecipients.toArray(new Address[0]);
    }

    @Override
    public void setRecipients(Message.RecipientType recipientType, Address[] addresses) throws MessagingException {
        getRecipientsList(recipientType).addAll(Arrays.asList(addresses));
    }

    @Override
    public void setRecipients(Message.RecipientType recipientType, String recipient) throws MessagingException {
        setRecipients(recipientType, InternetAddress.parse(recipient));
    }

    @Override
    public void addRecipients(Message.RecipientType recipientType, Address[] addresses) throws MessagingException {
        getRecipientsList(recipientType).addAll(Arrays.asList(addresses));
    }

    @Override
    public void addRecipients(Message.RecipientType recipientType, String recipient) throws MessagingException {
        addRecipients(recipientType, InternetAddress.parse(recipient));
    }

    @Override
    public Address[] getReplyTo() throws MessagingException {
        return replyToAddresses.toArray(new Address[0]);
    }

    @Override
    public void setReplyTo(Address[] addresses) throws MessagingException {
        replyToAddresses.addAll(Arrays.asList(addresses));
    }

    @Override
    public String getSubject() throws MessagingException {
        return subject;
    }

    @Override
    public void setSubject(String subject) throws MessagingException {
        this.subject = subject;
    }

    @Override
    public void setSubject(String subject, String charset) throws MessagingException {
        if (subject == null) {
            this.subject = null;
            return;
        }
        try {
            this.subject = new String(subject.getBytes(charset));
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("setting subject failed", e);
        }
    }

    @Override
    public Date getSentDate() throws MessagingException {
        return sentDate;
    }

    @Override
    public void setSentDate(Date date) throws MessagingException {
        sentDate = date;
    }

    @Override
    public Date getReceivedDate() throws MessagingException {
        return null; // trivial implementation
    }

    @Override
    public int getSize() throws MessagingException {
        return -1; // trivial implementation
    }

    @Override
    public int getLineCount() throws MessagingException {
        return -1; // trivial implementation
    }

    @Override
    public String getContentType() throws MessagingException {
        return getHeader("Content-Type", null);
    }

    @Override
    public boolean isMimeType(String mimeType) throws MessagingException {
        return mimeType.startsWith(getContentType());
    }

    @Override
    public String getDisposition() throws MessagingException {
        return getHeader("Content-Disposition", null);
    }

    @Override
    public void setDisposition(String disposition) throws MessagingException {
        setHeader("Content-Disposition", disposition);
    }

    @Override
    public String getEncoding() throws MessagingException {
        return getHeader("Content-Transfer-Encoding", null);
    }

    @Override
    public String getContentID() throws MessagingException {
        return getHeader("Content-ID", null);
    }

    @Override
    public void setContentID(String contentID) throws MessagingException {
        setHeader("Content-ID", contentID);
    }

    @Override
    public String getContentMD5() throws MessagingException {
        return getHeader("Content-MD5", null);
    }

    @Override
    public void setContentMD5(String value) throws MessagingException {
        setHeader("Content-MD5", value);
    }

    @Override
    public String getDescription() throws MessagingException {
        return getHeader("Content-Description", null);
    }

    @Override
    public void setDescription(String description) throws MessagingException {
        setHeader("Content-Description", description);
    }

    @Override
    public void setDescription(String description, String charset) throws MessagingException {
        try {
            setDescription(new String(description.getBytes(charset)));
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("setting description failed", e);
        }
    }

    @Override
    public String[] getContentLanguage() throws MessagingException {
        return contentLanguage;
    }

    @Override
    public void setContentLanguage(String[] contentLanguage) throws MessagingException {
        this.contentLanguage = contentLanguage;
    }

    @Override
    public String getMessageID() throws MessagingException {
        return "ID-" + messageNumber; // trivial implementation
    }

    @Override
    public String getFileName() throws MessagingException {
        return fileName;
    }

    @Override
    public void setFileName(String fileName) throws MessagingException {
        this.fileName = fileName;
    }

    @Override
    public InputStream getInputStream() throws IOException, MessagingException {
        return null; // trivial implementation
    }

    @Override
    protected InputStream getContentStream() throws MessagingException {
        return null; // trivial implementation
    }

    @Override
    public InputStream getRawInputStream() throws MessagingException {
        if (content instanceof String) {
            return new ByteArrayInputStream(content.toString().getBytes());
        }
        throw new UnsupportedOperationException("Unimplementated method");
    }

    @Override
    public synchronized DataHandler getDataHandler() throws MessagingException {
        return dataHandler;
    }

    @Override
    public synchronized void setDataHandler(DataHandler dataHandler) throws MessagingException {
        this.dataHandler = dataHandler;
    }

    @Override
    public Object getContent() throws IOException, MessagingException {
        return content;
    }

    @Override
    public void setContent(Object object, String mimeType) throws MessagingException {
        content = object; // trivial implementation
    }

    @Override
    public void setText(String string) throws MessagingException {
        setContent(string, "text/plain");
    }

    @Override
    public void setText(String string, String charset) throws MessagingException {
        try {
            setContent(new String(string.getBytes(charset)), "text/plain");
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("setting text content failed", e);
        }
    }

    @Override
    public void setContent(Multipart multipart) throws MessagingException {
        content = multipart;
    }

    @Override
    public Message reply(boolean b) throws MessagingException {
        return new MockMimeMessage(this); // trivial implementation
    }

    @Override
    public void writeTo(OutputStream outputStream) throws IOException, MessagingException {
        // trivial implementation
    }

    @Override
    public void writeTo(OutputStream outputStream, String[] strings) throws IOException, MessagingException {
        // trivial implementation
    }

    @Override
    public String[] getHeader(String name) throws MessagingException {
        String value = (String) contentHeaders.get(name);
        if (value == null) {
            return null;
        }
        return new String[]{value};
    }

    @Override
    public String getHeader(String name, String delimiter) throws MessagingException {
        String[] header = getHeader(name);
        if (header == null || header.length == 0) {
            return null;
        }
        return header[0];
    }

    @Override
    public void setHeader(String name, String value) throws MessagingException {
        addHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) throws MessagingException {
        contentHeaders.put(name, value);
    }

    @Override
    public void removeHeader(String name) throws MessagingException {
        contentHeaders.remove(name);
    }

    @Override
    public Enumeration<String> getAllHeaders() throws MessagingException {
        return Collections.enumeration(contentHeaders.values());
    }

    @Override
    public Enumeration<String> getMatchingHeaders(String[] names) throws MessagingException {
        ArrayList<String> matchingHeaders = new ArrayList<>();
        for (String name : names) {
            String value = getHeader(name, null);
            if (value == null) {
                continue;
            }
            matchingHeaders.add(value);
        }
        return Collections.enumeration(matchingHeaders);
    }

    @Override
    public Enumeration<String> getNonMatchingHeaders(String[] names) throws MessagingException {
        List<String> existingHeaders = Arrays.asList(names);

        ArrayList<String> nonMatchingHeaders = new ArrayList<>();

        for (String name : contentHeaders.keySet()) {
            if (existingHeaders.contains(name)) {
                continue;
            }
            String value = getHeader(name, null);
            if (value == null) {
                continue;
            }
            nonMatchingHeaders.add(value);
        }
        return Collections.enumeration(nonMatchingHeaders);
    }

    @Override
    public void addHeaderLine(String headerLine) throws MessagingException {
        int separatorIndex = headerLine.indexOf(":");
        if (separatorIndex < 0) {
            throw new MessagingException("header line does not conform to standard");
        }

        addHeader(headerLine.substring(0, separatorIndex), headerLine.substring(separatorIndex, headerLine.length()));
    }

    @Override
    public Enumeration<String> getAllHeaderLines() throws MessagingException {
        return Collections.enumeration(getHeadersAsStrings(contentHeaders));
    }

    private List<String> getHeadersAsStrings(HashMap<String, String> contentHeaders) {
        return contentHeaders.entrySet()
            .stream()
            .map(entry -> entry.getKey() + ":" + entry.getValue())
            .collect(Guavate.toImmutableList());
    }

    @Override
    public Enumeration<String> getMatchingHeaderLines(String[] names) throws MessagingException {
        ArrayList<String> matchingHeaders = new ArrayList<>();
        for (String name : names) {
            String value = getHeader(name, null);
            if (value == null) {
                continue;
            }
            matchingHeaders.add(name + ":" + value);
        }
        return Collections.enumeration(matchingHeaders);
    }

    @Override
    public Enumeration<String> getNonMatchingHeaderLines(String[] names) throws MessagingException {
        List<String> existingHeaders = names != null ? Arrays.asList(names) : null;

        ArrayList<String> nonMatchingHeaders = new ArrayList<>();

        for (String name : contentHeaders.keySet()) {
            if (existingHeaders != null && existingHeaders.contains(name)) {
                continue;
            }
            String value = getHeader(name, null);
            if (value == null) {
                continue;
            }
            nonMatchingHeaders.add(name + ":" + value);
        }
        return Collections.enumeration(nonMatchingHeaders);
    }

    @Override
    public synchronized Flags getFlags() throws MessagingException {
        return new Flags(setFlags);
    }

    @Override
    public synchronized boolean isSet(Flags.Flag flag) throws MessagingException {
        return setFlags.contains(flag);
    }

    @Override
    public synchronized void setFlags(Flags flags, boolean set) throws MessagingException {
        if (set) {
            setFlags.add(flags);
        } else {
            setFlags.remove(flags);
        }
    }

    @Override
    public void saveChanges() throws MessagingException {
        // trivial implementation
    }

    @Override
    protected void updateHeaders() throws MessagingException {
        // trivial implementation
    }

    @Override
    protected InternetHeaders createInternetHeaders(InputStream inputStream) throws MessagingException {
        return new InternetHeaders();
    }

    @Override
    public void setRecipient(Message.RecipientType recipientType, Address address) throws MessagingException {
        setRecipients(recipientType, new Address[]{address});
    }

    @Override
    public void addRecipient(Message.RecipientType recipientType, Address address) throws MessagingException {
        setRecipients(recipientType, new Address[]{address});
    }

    @Override
    public void setFlag(Flags.Flag flag, boolean set) throws MessagingException {
        if (set) {
            setFlags.add(flag);
        } else {
            setFlags.remove(flag);
        }
    }

    @Override
    public int getMessageNumber() {
        return messageNumber;
    }

    @Override
    protected void setMessageNumber(int i) {
        messageNumber = i;
    }

    @Override
    public Folder getFolder() {
        return null;
    }

    @Override
    public boolean isExpunged() {
        return isExpunged;
    }

    @Override
    protected void setExpunged(boolean b) {
        isExpunged = b;
    }

    public void setShouldMatch(boolean doMatch) {
        this.doMatch = doMatch;
    }

    @Override
    public boolean match(SearchTerm searchTerm) throws MessagingException {
        return doMatch;
    }
}
