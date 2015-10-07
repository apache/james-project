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

import org.apache.mailet.HostAddress;
import org.apache.mailet.LookupException;
import org.apache.mailet.MailetContext;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.TemporaryLookupException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class FakeMailContext implements MailetContext {
    
    HashMap<String, Object> attributes = new HashMap<String, Object>();

    public void bounce(Mail mail, String message) throws MessagingException {
        // trivial implementation
    }

    public void bounce(Mail mail, String message, MailAddress bouncer) throws MessagingException {
        // trivial implementation
    }

    /**
     * @deprecated use the generic dnsLookup method
     */
    public Collection<String> getMailServers(String host) {
        return null;  // trivial implementation
    }

    public MailAddress getPostmaster() {
        return null;  // trivial implementation
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public Iterator<String> getAttributeNames() {
        return attributes.keySet().iterator();
    }

    public int getMajorVersion() {
        return 0;  // trivial implementation
    }

    public int getMinorVersion() {
        return 0;  // trivial implementation
    }

    public String getServerInfo() {
        return "Mock Server";
    }

    public boolean isLocalServer(String serverName) {
        return false;  // trivial implementation
    }

    /**
     * @deprecated use {@link #isLocalEmail(MailAddress)} instead 
     */
    public boolean isLocalUser(String userAccount) {
        return false;  // trivial implementation
    }

    public boolean isLocalEmail(MailAddress mailAddress) {
        return false;  // trivial implementation
    }

    /**
     * @deprecated use {@link #log(LogLevel level, String message)}
     */
    public void log(String message) {
        System.out.println(message);
    }

    /**
     * @deprecated use {@link #log(LogLevel level, String message, Throwable t)}
     */
    public void log(String message, Throwable t) {
        System.out.println(message);
        t.printStackTrace(System.out);
    }

    public void removeAttribute(String name) {
        // trivial implementation
    }

    public void sendMail(MimeMessage msg) throws MessagingException {
        throw new UnsupportedOperationException("MOCKed method");
    }

    public void sendMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage msg) throws MessagingException {
        throw new UnsupportedOperationException("MOCKed method");
    }

    public void sendMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage msg, String state) throws MessagingException {
        throw new UnsupportedOperationException("MOCKed method");
    }

    public void sendMail(Mail mail) throws MessagingException {
        throw new UnsupportedOperationException("MOCKed method");
    }

    public void setAttribute(String name, Serializable object) {
        attributes.put(name,object);
    }

    public void storeMail(MailAddress sender, MailAddress recipient, MimeMessage msg) throws MessagingException {
        // trivial implementation
    }

    /**
     * @deprecated use the generic dnsLookup method
     */
    public Iterator<HostAddress> getSMTPHostAddresses(String domainName) {
        return null;  // trivial implementation
    }

    public void setAttribute(String name, Object value) {
        throw new UnsupportedOperationException("MOCKed method");
    }

    public void log(LogLevel level, String message) {
        System.out.println("[" + level + "]" + message);
    }

    public void log(LogLevel level, String message, Throwable t) {
        System.out.println("[" + level + "]" + message);
        t.printStackTrace(System.out);
    }

    public List<String> dnsLookup(String name, RecordType type) throws TemporaryLookupException, LookupException {
        return null;   // trivial implementation
    }
}
