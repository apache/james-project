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
package org.apache.james.mailetcontainer.api.mock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.HostAddress;
import org.apache.mailet.LookupException;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
public class MockMailetContext implements MailetContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockMailetContext.class);

    private final Map<String, Object> attributes = new HashMap<>();
    private final List<Mail> mails = new ArrayList<>();

    @Override
    public void bounce(Mail arg0, String arg1) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void bounce(Mail arg0, String arg1, MailAddress arg2) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<String> dnsLookup(String s, RecordType recordType) throws LookupException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Object getAttribute(String arg0) {
        return attributes.get(arg0);
    }

    @Override
    public Iterator<String> getAttributeNames() {
        return attributes.keySet().iterator();
    }

    /**
     * Return an {@link Collection} which holds "mx.localhost" if the given host
     * was "localhost". Otherwise and empty {@link Collection} is returned
     */
    @Override
    public Collection<String> getMailServers(String host) {
        List<String> servers = new ArrayList<>();
        if ("localhost".equalsIgnoreCase(host)) {
            servers.add("mx.localhost");
        }
        return servers;
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public MailAddress getPostmaster() {
        try {
            return new MailAddress("postmaster@localhost");
        } catch (AddressException e) {
            // will never happen
            return null;
        }
    }

    @Override
    public Iterator<HostAddress> getSMTPHostAddresses(String arg0) {
        return new ArrayList<HostAddress>().iterator();
    }

    @Override
    public String getServerInfo() {
        return "Mock Server";
    }

    /**
     * @see #isLocalUser(String)
     */
    @Override
    public boolean isLocalEmail(MailAddress arg0) {
        return isLocalUser(arg0.toString());
    }

    /**
     * Return true if "localhost" was given
     */
    @Override
    public boolean isLocalServer(String server) {
        return "localhost".equalsIgnoreCase(server);
    }

    /**
     * Return true if "localuser@localhost" was given
     */
    @Override
    public boolean isLocalUser(String user) {
        return "localuser@localhost".equalsIgnoreCase(user);
    }

    @Override
    public void log(String msg) {
        System.out.println(msg);
    }

    @Override
    public void log(String arg0, Throwable arg1) {
        System.out.println(arg0);
        arg1.printStackTrace();
    }

    @Override
    public void log(LogLevel logLevel, String s) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void log(LogLevel logLevel, String s, Throwable throwable) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void removeAttribute(String arg0) {
        attributes.remove(arg0);
    }

    @Override
    public void sendMail(MimeMessage arg0) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void sendMail(Mail mail) throws MessagingException {
        mails.add(mail);
    }

    @Override
    public void sendMail(MailAddress arg0, Collection<MailAddress> arg1, MimeMessage arg2) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void sendMail(MailAddress arg0, Collection<MailAddress> arg1, MimeMessage arg2, String arg3) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setAttribute(String arg0, Object arg1) {
        attributes.put(arg0, arg1);
    }

    @Override
    public Logger getLogger() {
        return LOGGER;
    }
}
