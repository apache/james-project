/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.transport.mailets.managesieve;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

public class MockMail implements Mail {

    private static final long serialVersionUID = 6535523029509031395L;
    
    private MimeMessage message = null;
    private Map<String, Serializable> attributes = new HashMap<String, Serializable>();

    @Override
    public Serializable getAttribute(String arg0) {
        return attributes.get(arg0);
    }

    @Override
    public Iterator<String> getAttributeNames() {
        return attributes.keySet().iterator();
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

    @Override
    public Date getLastUpdated() {
        return null;
    }

    @Override
    public MimeMessage getMessage() throws MessagingException {
        return message;
    }

    @Override
    public long getMessageSize() throws MessagingException {
        return 0;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Collection<MailAddress> getRecipients() {
        return null;
    }

    @Override
    public String getRemoteAddr() {
        return null;
    }

    @Override
    public String getRemoteHost() {
        return null;
    }

    @Override
    public MailAddress getSender() {
        MailAddress addr = null;
        try {
            addr = new MailAddress((InternetAddress)message.getSender());
        } catch (AddressException ex) {
        } catch (MessagingException ex) {
        }
        return addr;
    }

    @Override
    public String getState() {
        return null;
    }

    @Override
    public boolean hasAttributes() {
        return false;
    }

    @Override
    public void removeAllAttributes() {

    }

    @Override
    public Serializable removeAttribute(String arg0) {
        return null;
    }

    @Override
    public Serializable setAttribute(String arg0, Serializable arg1) {
        return attributes.put(arg0, arg1);
    }

    @Override
    public void setErrorMessage(String arg0) {

    }

    @Override
    public void setLastUpdated(Date arg0) {

    }

    @Override
    public void setMessage(MimeMessage arg0) {
        message = arg0;
    }

    @Override
    public void setName(String arg0) {
    }

    @Override
    public void setRecipients(Collection<MailAddress> arg0) {

    }

    @Override
    public void setState(String arg0) {
    }

}
