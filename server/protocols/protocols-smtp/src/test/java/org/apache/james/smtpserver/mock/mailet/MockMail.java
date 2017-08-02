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
package org.apache.james.smtpserver.mock.mailet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang.NotImplementedException;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.PerRecipientHeaders.Header;

public class MockMail implements Mail {

    private MimeMessage msg = null;
    private Collection<MailAddress> recipients = new ArrayList<>();
    private String name = null;
    private final MailAddress sender = null;
    private String state = null;
    private String errorMessage;
    private Date lastUpdated;
    private final HashMap<String, Serializable> attributes = new HashMap<>();
    private static final long serialVersionUID = 1L;
    private long size = 0;
    private String remoteAddr = "127.0.0.1";

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String newName) {
        this.name = newName;
    }

    @Override
    public MimeMessage getMessage() throws MessagingException {
        return msg;
    }

    @Override
    public Collection<MailAddress> getRecipients() {
        return recipients;
    }

    @Override
    public void setRecipients(Collection<MailAddress> recipients) {
        this.recipients = recipients;
    }

    @Override
    public MailAddress getSender() {
        return sender;
    }

    @Override
    public String getState() {
        return state;
    }

    @Override
    public String getRemoteHost() {
        return "111.222.333.444";
    }

    @Override
    public String getRemoteAddr() {
        return remoteAddr;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public void setErrorMessage(String msg) {
        this.errorMessage = msg;
    }

    @Override
    public void setMessage(MimeMessage message) {
        this.msg = message;
    }

    @Override
    public void setState(String state) {
        this.state = state;
    }

    @Override
    public Serializable getAttribute(String name) {
        return (Serializable) attributes.get(name);
    }

    @Override
    public Iterator<String> getAttributeNames() {
        return attributes.keySet().iterator();
    }

    @Override
    public boolean hasAttributes() {
        return !attributes.isEmpty();
    }

    @Override
    public Serializable removeAttribute(String name) {
        return (Serializable) attributes.remove(name);

    }

    @Override
    public void removeAllAttributes() {
        attributes.clear();
    }

    @Override
    public Serializable setAttribute(String name, Serializable object) {

        return (Serializable) attributes.put(name, object);
    }

    @Override
    public long getMessageSize() throws MessagingException {
        return size;
    }

    @Override
    public Date getLastUpdated() {
        return lastUpdated;
    }

    @Override
    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void setMessageSize(long size) {
        this.size = size;
    }

    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    @Override
    public PerRecipientHeaders getPerRecipientSpecificHeaders() {
        throw new NotImplementedException();
    }

    @Override
    public void addSpecificHeaderForRecipient(Header header, MailAddress recipient) {
        throw new NotImplementedException();
    }
}
