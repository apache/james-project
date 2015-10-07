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

import java.io.*;
import java.util.*;
import javax.activation.DataHandler;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeMessage;
import javax.mail.search.SearchTerm;

public class MockMimeMessage extends MimeMessage {

    private final List m_fromAddresses = new ArrayList();
    private Address m_senderAddress;
    private final List m_toRecepients = new ArrayList();
    private final List m_ccRecepients = new ArrayList();
    private final List m_bccRecepients = new ArrayList();
    private final List m_replyToAddresses = new ArrayList();
    private String m_subject;
    private int m_iMessageNumber;
    private boolean m_bIsExpunged;
    private Object m_content;
    private Date m_sentDate;
    private String[] m_contentLanguage;
    private String m_fileName;
    private DataHandler m_dataHandler;
    private final HashMap m_contentHeaders = new HashMap();
    private final Flags m_setFlags = new Flags();
    private boolean m_doMatch;

    public MockMimeMessage() {
        super((Session) null);
    }

    public MockMimeMessage(int messageNumber) {
        super((Session) null);
        m_iMessageNumber = messageNumber;
    }

    public MockMimeMessage(MimeMessage mimeMessage) throws MessagingException {
        super(mimeMessage); // trivial implementation
    }

    @Override
    public Address[] getFrom() throws MessagingException {
        return (Address[]) m_fromAddresses.toArray();
    }

    @Override
    public void setFrom(Address address) throws MessagingException {
        m_fromAddresses.clear();
        m_fromAddresses.add(address);
    }

    @Override
    public void setFrom() throws MessagingException {
        m_fromAddresses.clear();
        m_fromAddresses.add(InternetAddress.getLocalAddress(null));
    }

    @Override
    public void addFrom(Address[] addresses) throws MessagingException {
        m_fromAddresses.add(addresses);
    }

    @Override
    public Address getSender() throws MessagingException {
        return m_senderAddress;
    }

    @Override
    public void setSender(Address address) throws MessagingException {
        m_senderAddress = address;
    }

    @Override
    public Address[] getRecipients(Message.RecipientType recipientType) throws MessagingException {
        List recipientsList = getRecipientsList(recipientType);
        List recipientAddresses = new ArrayList();
        for (Object aRecipientsList : recipientsList) {
            String recipient = (String) aRecipientsList;
            recipientAddresses.add(new InternetAddress(recipient));
        }
        return (Address[]) (recipientAddresses.toArray(new Address[recipientAddresses.size()]));
    }

    private List getRecipientsList(Message.RecipientType recipientType) {
        if (Message.RecipientType.TO.equals(recipientType)) {
            return m_toRecepients;
        }
        if (Message.RecipientType.CC.equals(recipientType)) {
            return m_ccRecepients;
        }
        if (Message.RecipientType.BCC.equals(recipientType)) {
            return m_bccRecepients;
        }
        return null;
    }

    @Override
    public Address[] getAllRecipients() throws MessagingException {
        List allRecipients = new ArrayList();
        allRecipients.addAll(m_toRecepients);
        allRecipients.addAll(m_ccRecepients);
        allRecipients.addAll(m_bccRecepients);
        return (Address[]) allRecipients.toArray();
    }

    @Override
    public void setRecipients(Message.RecipientType recipientType, Address[] addresses) throws MessagingException {
        getRecipientsList(recipientType).addAll(Arrays.asList(addresses));
    }

    @Override
    public void setRecipients(Message.RecipientType recipientType, String recipient) throws MessagingException {
        getRecipientsList(recipientType).add(recipient);
    }

    @Override
    public void addRecipients(Message.RecipientType recipientType, Address[] addresses) throws MessagingException {
        getRecipientsList(recipientType).addAll(Arrays.asList(addresses));
    }

    @Override
    public void addRecipients(Message.RecipientType recipientType, String recipient) throws MessagingException {
        getRecipientsList(recipientType).add(recipient);
    }

    @Override
    public Address[] getReplyTo() throws MessagingException {
        return (Address[]) m_replyToAddresses.toArray();
    }

    @Override
    public void setReplyTo(Address[] addresses) throws MessagingException {
        m_replyToAddresses.addAll(Arrays.asList(addresses));
    }

    @Override
    public String getSubject() throws MessagingException {
        return m_subject;
    }

    @Override
    public void setSubject(String subject) throws MessagingException {
        m_subject = subject;
    }

    @Override
    public void setSubject(String subject, String charset) throws MessagingException {
        if (subject == null) {
            m_subject = null;
            return;
        }
        try {
            m_subject = new String(subject.getBytes(charset));
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("setting subject failed", e);
        }
    }

    @Override
    public Date getSentDate() throws MessagingException {
        return m_sentDate;
    }

    @Override
    public void setSentDate(Date date) throws MessagingException {
        m_sentDate = date;
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
        return m_contentLanguage;
    }

    @Override
    public void setContentLanguage(String[] contentLanguage) throws MessagingException {
        m_contentLanguage = contentLanguage;
    }

    @Override
    public String getMessageID() throws MessagingException {
        return "ID-" + m_iMessageNumber; // trivial implementation
    }

    @Override
    public String getFileName() throws MessagingException {
        return m_fileName;
    }

    @Override
    public void setFileName(String fileName) throws MessagingException {
        m_fileName = fileName;
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
        if (m_content instanceof String) {
            return new ByteArrayInputStream(m_content.toString().getBytes());
        }
        throw new UnsupportedOperationException("Unimplementated method");
    }

    @Override
    public synchronized DataHandler getDataHandler() throws MessagingException {
        return m_dataHandler;
    }

    @Override
    public synchronized void setDataHandler(DataHandler dataHandler) throws MessagingException {
        m_dataHandler = dataHandler;
    }

    @Override
    public Object getContent() throws IOException, MessagingException {
        return m_content;
    }

    @Override
    public void setContent(Object object, String mimeType) throws MessagingException {
        m_content = object; // trivial implementation
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
        m_content = multipart;
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
        String value = (String) m_contentHeaders.get(name);
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
        m_contentHeaders.put(name, value);
    }

    @Override
    public void removeHeader(String name) throws MessagingException {
        m_contentHeaders.remove(name);
    }

    @Override
    public Enumeration getAllHeaders() throws MessagingException {
        return Collections.enumeration(m_contentHeaders.values());
    }

    @Override
    public Enumeration getMatchingHeaders(String[] names) throws MessagingException {
        ArrayList matchingHeaders = new ArrayList();
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
    public Enumeration getNonMatchingHeaders(String[] names) throws MessagingException {
        List existingHeaders = Arrays.asList(names);

        ArrayList nonMatchingHeaders = new ArrayList();

        for (Object o : m_contentHeaders.keySet()) {
            String name = (String) o;
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
    public Enumeration getAllHeaderLines() throws MessagingException {
        return Collections.enumeration(getHeadersAsStrings(m_contentHeaders));
    }

    private ArrayList getHeadersAsStrings(HashMap contentHeaders) {
        ArrayList headerLines = new ArrayList();
        for (Object o : contentHeaders.keySet()) {
            String key = (String) o;
            String value = (String) contentHeaders.get(key);
            headerLines.add(key + ":" + value);
        }
        return headerLines;
    }

    @Override
    public Enumeration getMatchingHeaderLines(String[] names) throws MessagingException {
        ArrayList matchingHeaders = new ArrayList();
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
    public Enumeration getNonMatchingHeaderLines(String[] names) throws MessagingException {
        List existingHeaders = names != null ? Arrays.asList(names) : null;

        ArrayList nonMatchingHeaders = new ArrayList();

        for (Object o : m_contentHeaders.keySet()) {
            String name = (String) o;
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
        return new Flags(m_setFlags);
    }

    @Override
    public synchronized boolean isSet(Flags.Flag flag) throws MessagingException {
        return m_setFlags.contains(flag);
    }

    @Override
    public synchronized void setFlags(Flags flags, boolean set) throws MessagingException {
        if (set) {
            m_setFlags.add(flags);
        } else {
            m_setFlags.remove(flags);
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
            m_setFlags.add(flag);
        } else {
            m_setFlags.remove(flag);
        }
    }

    @Override
    public int getMessageNumber() {
        return m_iMessageNumber;
    }

    @Override
    protected void setMessageNumber(int i) {
        m_iMessageNumber = i;
    }

    @Override
    public Folder getFolder() {
        return null;
    }

    @Override
    public boolean isExpunged() {
        return m_bIsExpunged;
    }

    @Override
    protected void setExpunged(boolean b) {
        m_bIsExpunged = b;
    }

    public void setShouldMatch(boolean doMatch) {
        m_doMatch = doMatch;
    }

    @Override
    public boolean match(SearchTerm searchTerm) throws MessagingException {
        return m_doMatch;
    }
}
