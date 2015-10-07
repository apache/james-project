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


package org.apache.james.transport.matchers;

import org.apache.james.transport.matchers.HostIsLocal;
import org.apache.mailet.HostAddress;
import org.apache.mailet.LookupException;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;
import org.apache.mailet.Matcher;
import org.apache.mailet.TemporaryLookupException;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.Assert;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class HostIsLocalTest {

    private FakeMail mockedMail;

    private Matcher matcher;

    private final String[] LOCALSERVER = new String[]{"james.apache.org"};

    private MailAddress[] recipients;

    private void setRecipients(MailAddress[] recipients) {
        this.recipients = recipients;
    }

    private void setupMockedMail() {
        mockedMail = new FakeMail();
        mockedMail.setRecipients(Arrays.asList(recipients));

    }

    private void setupMatcher() throws MessagingException {

        MailetContext FakeMailContext = new MailetContext() {

            Collection<String> localServer = new ArrayList<String>(Arrays.asList(LOCALSERVER));

            public void bounce(Mail mail, String message)
                    throws MessagingException {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");

            }

            public void bounce(Mail mail, String message, MailAddress bouncer)
                    throws MessagingException {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");

            }

            public Collection<String> getMailServers(String host) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public MailAddress getPostmaster() {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public Object getAttribute(String name) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public Iterator<String> getAttributeNames() {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public int getMajorVersion() {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public int getMinorVersion() {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public String getServerInfo() {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public boolean isLocalServer(String serverName) {
                return localServer.contains(serverName);
            }

            public boolean isLocalUser(String userAccount) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public boolean isLocalEmail(MailAddress mailAddress) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void log(String message) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void log(String message, Throwable t) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void removeAttribute(String name) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void sendMail(MimeMessage msg) throws MessagingException {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void sendMail(MailAddress sender, Collection recipients,
                                 MimeMessage msg) throws MessagingException {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void sendMail(MailAddress sender, Collection recipients,
                                 MimeMessage msg, String state) throws MessagingException {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void sendMail(Mail mail) throws MessagingException {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void setAttribute(String name, Object object) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public Iterator<HostAddress> getSMTPHostAddresses(String domainName) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void storeMail(MailAddress arg0, MailAddress arg1,
                                  MimeMessage arg2) throws MessagingException {
                // TODO Auto-generated method stub

            }

            public void log(LogLevel level, String message) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void log(LogLevel level, String message, Throwable t) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public List<String> dnsLookup(String name, RecordType type) throws TemporaryLookupException, LookupException {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }


        };

        matcher = new HostIsLocal();
        FakeMatcherConfig mci = new FakeMatcherConfig("HostIsLocal",
                FakeMailContext);
        matcher.init(mci);
    }

    // test if all recipients get returned as matched
    @Test
    public void testHostIsMatchedAllRecipients() throws MessagingException {
        setRecipients(new MailAddress[]{
                new MailAddress("test@james.apache.org"),
                new MailAddress("test2@james.apache.org")});

        setupMockedMail();
        setupMatcher();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        Assert.assertNotNull(matchedRecipients);
        Assert.assertEquals(matchedRecipients.size(), mockedMail.getRecipients()
                .size());
    }

    // test if one recipients get returned as matched
    @Test
    public void testHostIsMatchedOneRecipient() throws MessagingException {
        setRecipients(new MailAddress[]{
                new MailAddress("test@james2.apache.org"),
                new MailAddress("test2@james.apache.org")});

        setupMockedMail();
        setupMatcher();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        Assert.assertNotNull(matchedRecipients);
        Assert.assertEquals(matchedRecipients.size(), 1);
    }

    // test if no recipient get returned cause it not match
    @Test
    public void testHostIsNotMatch() throws MessagingException {
        setRecipients(new MailAddress[]{
                new MailAddress("test@james2.apache.org"),
                new MailAddress("test2@james2.apache.org")});

        setupMockedMail();
        setupMatcher();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        Assert.assertEquals(matchedRecipients.size(), 0);
    }
}
