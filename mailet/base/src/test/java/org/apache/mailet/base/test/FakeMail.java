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


package org.apache.mailet.base.test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

public class FakeMail implements Mail {

    private MimeMessage msg = null;

    private Collection<MailAddress> recipients = new ArrayList<MailAddress>();

    private String name = null;

    private MailAddress sender = null;

    private String state = null;

    private String errorMessage;

    private Date lastUpdated;

    private HashMap<String, Serializable> attributes = new HashMap<String, Serializable>();

    private static final long serialVersionUID = 1L;

    private long size = 0;
    
    private String remoteAddr ="127.0.0.1";
    
    public FakeMail() {
        super();
    }

    public FakeMail(MimeMessage msg) {
        this();
        this.msg = msg;
    }

    public String getName() {
        return name;
    }

    public void setName(String newName) {
        this.name = newName;
    }

    public MimeMessage getMessage() throws MessagingException {
        return msg;
    }

    public Collection<MailAddress> getRecipients() {
        return recipients;
    }

    public void setRecipients(Collection<MailAddress> recipients) {
        this.recipients = recipients;
    }

    public MailAddress getSender() {
        return sender;
    }

    public String getState() {
        return state;
    }

    public String getRemoteHost() {
        return "111.222.333.444";
    }

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String msg) {
        this.errorMessage = msg;
    }

    public void setMessage(MimeMessage message) {
        this.msg = message;
        try {
            if (message != null && message.getSender() != null) {
                this.sender = new MailAddress((InternetAddress) message.getSender());
            }
        } catch (MessagingException e) {
            throw new RuntimeException("Exception caught", e);
        }
    }

    public void setState(String state) {
        this.state = state;
    }

    public Serializable getAttribute(String name) {
        return attributes.get(name);
    }

    public Iterator<String> getAttributeNames() {
        return attributes.keySet().iterator();
    }

    public boolean hasAttributes() {
        return !attributes.isEmpty();
    }

    public Serializable removeAttribute(String name) {
        return attributes.remove(name);

    }

    public void removeAllAttributes() {
        attributes.clear();
    }

    public Serializable setAttribute(String name, Serializable object) {

        return attributes.put(name, object);
    }

    public long getMessageSize() throws MessagingException {
        return size;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    public void setMessageSize(long size) {
        this.size = size;
    }

    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    public void setSender(MailAddress sender) {
        this.sender = sender;
    }
}
