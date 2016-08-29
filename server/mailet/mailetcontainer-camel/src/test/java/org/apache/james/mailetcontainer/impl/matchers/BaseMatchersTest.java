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

package org.apache.james.mailetcontainer.impl.matchers;

import java.util.Arrays;

import javax.mail.MessagingException;

import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIs;
import org.apache.mailet.MailAddress;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.Before;

public class BaseMatchersTest {

    protected FakeMailContext context;
    protected FakeMail mockedMail;
    protected CompositeMatcher matcher;

    @Before
    public void setUp() throws Exception {
        mockedMail = new FakeMail();
        mockedMail.setRecipients(Arrays.asList(new MailAddress("test@james.apache.org"), new MailAddress(
                "test2@james.apache.org")));
    }

    void setupCompositeMatcher(String matcherName, Class<? extends GenericCompositeMatcher> matcherClass)
            throws Exception {
        context = FakeMailContext.defaultContext();
        matcher = matcherClass.newInstance();
        FakeMatcherConfig mci = new FakeMatcherConfig(matcherName, context);
        matcher.init(mci);
    }

    void setupChild(String matcherName) throws MessagingException {
        Matcher child;
        if (matcherName.equals("All")) {
            child = new All();
        }
        else {
            child = new RecipientIs();
        }
        FakeMatcherConfig sub = new FakeMatcherConfig(matcherName, context);
        child.init(sub);
        matcher.add(child);
    }
}
