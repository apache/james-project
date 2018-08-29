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

package org.apache.james.jmap;

import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.jmap.mailet.VacationMailet;
import org.apache.james.mailetcontainer.impl.MatcherMailetPair;
import org.apache.james.modules.server.CamelMailetContainerModule;
import org.apache.james.transport.mailets.Null;
import org.apache.james.transport.mailets.RemoveMimeHeader;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Test;

import com.google.common.collect.Lists;

public class MailetPreconditionTest {

    private static final MailetContext MAILET_CONTEXT = null;
    private static final String BCC = "bcc";

    @Test(expected = ConfigurationException.class)
    public void vacationMailetCheckShouldThrowOnEmptyList() throws Exception {
        JMAPModule.VACATION_MAILET_CHECK.check(Lists.newArrayList());
    }

    @Test(expected = NullPointerException.class)
    public void vacationMailetCheckShouldThrowOnNullList() throws Exception {
        JMAPModule.VACATION_MAILET_CHECK.check(null);
    }

    @Test(expected = ConfigurationException.class)
    public void vacationMailetCheckShouldThrowOnWrongMatcher() throws Exception {
        List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new All(), new VacationMailet(null, null, null, null, null)));
        JMAPModule.VACATION_MAILET_CHECK.check(pairs);
    }

    @Test(expected = ConfigurationException.class)
    public void vacationMailetCheckShouldThrowOnWrongMailet() throws Exception {
        List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new RecipientIsLocal(), new Null()));
        JMAPModule.VACATION_MAILET_CHECK.check(pairs);
    }

    @Test
    public void vacationMailetCheckShouldNotThrowIfValidPairPresent() throws Exception {
        List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new RecipientIsLocal(), new VacationMailet(null, null, null, null, null)));
        JMAPModule.VACATION_MAILET_CHECK.check(pairs);
    }

    @Test(expected = ConfigurationException.class)
    public void bccMailetCheckShouldThrowOnEmptyList() throws Exception {
        CamelMailetContainerModule.BCC_Check.check(Lists.newArrayList());
    }

    @Test(expected = NullPointerException.class)
    public void bccMailetCheckShouldThrowOnNullList() throws Exception {
        CamelMailetContainerModule.BCC_Check.check(null);
    }

    @Test(expected = ConfigurationException.class)
    public void bccMailetCheckShouldThrowOnWrongMatcher() throws Exception {
        List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new RecipientIsLocal(),  new RemoveMimeHeader()));
        CamelMailetContainerModule.BCC_Check.check(pairs);
    }

    @Test(expected = ConfigurationException.class)
    public void bccMailetCheckShouldThrowOnWrongMailet() throws Exception {
        List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new All(), new Null()));
        CamelMailetContainerModule.BCC_Check.check(pairs);
    }

    @Test(expected = ConfigurationException.class)
    public void bccMailetCheckShouldThrowOnWrongFieldName() throws Exception {
        RemoveMimeHeader removeMimeHeader = new RemoveMimeHeader();
        removeMimeHeader.init(FakeMailetConfig.builder()
            .mailetName(BCC)
            .mailetContext(MAILET_CONTEXT)
            .setProperty("name", "bad")
            .build());

        List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new All(), removeMimeHeader));
        CamelMailetContainerModule.BCC_Check.check(pairs);
    }

    @Test
    public void bccMailetCheckShouldNotThrowOnValidPair() throws Exception {
        RemoveMimeHeader removeMimeHeader = new RemoveMimeHeader();
        removeMimeHeader.init(FakeMailetConfig.builder()
            .mailetName(BCC)
            .mailetContext(MAILET_CONTEXT)
            .setProperty("name", BCC)
            .build());

        List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new All(), removeMimeHeader));
        CamelMailetContainerModule.BCC_Check.check(pairs);
    }
}
