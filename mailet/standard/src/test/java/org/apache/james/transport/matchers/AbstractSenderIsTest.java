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

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.ParseException;

import junit.framework.TestCase;

import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.Matcher;

public abstract class AbstractSenderIsTest extends TestCase {

    protected Mail mockedMail;

    protected Matcher matcher;

    private MailAddress sender;

    public AbstractSenderIsTest(String arg0)
            throws UnsupportedEncodingException {
        super(arg0);
    }

    protected void setSender(MailAddress sender) {
        this.sender = sender;
    }

    protected void setupMockedMail() {
        mockedMail = new Mail() {

            private static final long serialVersionUID = 1L;

            public String getName() {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void setName(String newName) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public MimeMessage getMessage() throws MessagingException {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public Collection<MailAddress> getRecipients() {
                ArrayList<MailAddress> r = new ArrayList<MailAddress>();
                try {
                    r.add(new MailAddress("test@localhost"));
                } catch (ParseException ignored) {
                }
                return r;
            }

            public void setRecipients(Collection recipients) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public MailAddress getSender() {
                return sender;
            }

            public String getState() {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public String getRemoteHost() {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public String getRemoteAddr() {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public String getErrorMessage() {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void setErrorMessage(String msg) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void setMessage(MimeMessage message) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void setState(String state) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public Serializable getAttribute(String name) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public Iterator<String> getAttributeNames() {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public boolean hasAttributes() {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public Serializable removeAttribute(String name) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void removeAllAttributes() {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public Serializable setAttribute(String name, Serializable object) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public long getMessageSize() throws MessagingException {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public Date getLastUpdated() {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

            public void setLastUpdated(Date lastUpdated) {
                throw new UnsupportedOperationException(
                        "Unimplemented mock service");
            }

        };

    }

    protected void setupMatcher() throws MessagingException {
        matcher = createMatcher();
        FakeMatcherConfig mci = new FakeMatcherConfig(getConfigOption()
                + getConfigValue(), new FakeMailContext());
        matcher.init(mci);
    }

    protected void setupAll() throws MessagingException {
        setupMockedMail();
        setupMatcher();
    }

    protected abstract String getConfigOption();

    protected abstract String getConfigValue();

    protected abstract Matcher createMatcher();
}
