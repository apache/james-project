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

import org.apache.mailet.HostAddress;
import org.apache.mailet.LookupException;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class MockMailetContext implements MailetContext {

    private MimeMessage _message = null;

    @Override
    public void bounce(Mail mail, String s) throws MessagingException {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public void bounce(Mail mail, String s, MailAddress mailaddress) throws MessagingException {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public List<String> dnsLookup(String s, RecordType recordType) throws LookupException {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public Object getAttribute(String s) {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public Iterator<String> getAttributeNames() {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public Collection<String> getMailServers(String s) {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public int getMajorVersion() {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public int getMinorVersion() {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public MailAddress getPostmaster() {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @SuppressWarnings("deprecation")
    @Override
    public Iterator<HostAddress> getSMTPHostAddresses(String s) {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public String getServerInfo() {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public boolean isLocalEmail(MailAddress mailaddress) {
        return false;
    }

    @Override
    public boolean isLocalServer(String s) {
        return s.equals("localhost");
    }

    @Override
    public boolean isLocalUser(String s) {
        return false;
    }

    @Override
    public void log(String s) {
        System.out.println(s);
    }

    @Override
    public void log(String s, Throwable throwable) {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public void log(LogLevel logLevel, String s) {
        System.out.println(logLevel.name() + ": " + s);
    }

    @Override
    public void log(LogLevel logLevel, String s, Throwable throwable) {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public void removeAttribute(String s) {
        throw new UnsupportedOperationException("Not yet implemented!");

    }

    @Override
    public void sendMail(MimeMessage mimemessage) throws MessagingException {
        _message = mimemessage;
    }

    @Override
    public void sendMail(Mail mail) throws MessagingException {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    
    
    @Override
    public void sendMail(MailAddress mailaddress, Collection<MailAddress> collection, MimeMessage mimemessage)
            throws MessagingException {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public void sendMail(MailAddress mailaddress, Collection<MailAddress> collection, MimeMessage mimemessage,
                         String s) throws MessagingException {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public void setAttribute(String s, Object obj) {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    public void storeMail(MailAddress mailaddress, MailAddress mailaddress1, MimeMessage mimemessage) {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    public MimeMessage getMessage() {
        return _message;
    }

}
