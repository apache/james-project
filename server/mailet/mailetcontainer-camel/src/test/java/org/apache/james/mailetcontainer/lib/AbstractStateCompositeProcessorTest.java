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
package org.apache.james.mailetcontainer.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

import javax.mail.MessagingException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.core.MailImpl;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.api.mock.MockMailProcessor;
import org.apache.mailet.Mail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public abstract class AbstractStateCompositeProcessorTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testChooseRightProcessor() throws Exception {
        AbstractStateCompositeProcessor processor = new AbstractStateCompositeProcessor() {

            @Override
            protected MailProcessor createMailProcessor(final String state, HierarchicalConfiguration config) throws
                Exception {
                return new MockMailProcessor("") {

                    @Override
                    public void service(Mail mail) throws MessagingException {
                        // check if the right processor was selected depending on the state
                        assertEquals(state, mail.getState());
                        super.service(mail);
                    }
                };
            }
        };
        processor.configure(createConfig(Arrays.asList("root", "error", "test")));
        processor.init();

        try {
            Mail mail1 = new MailImpl();
            mail1.setState(Mail.DEFAULT);
            Mail mail2 = new MailImpl();
            mail2.setState(Mail.ERROR);

            Mail mail3 = new MailImpl();
            mail3.setState("test");

            Mail mail4 = new MailImpl();
            mail4.setState("invalid");

            processor.service(mail1);
            processor.service(mail2);
            processor.service(mail3);

            expectedException.expect(MessagingException.class);

            processor.service(mail4);
        } finally {
            processor.dispose();
        }

    }

    protected abstract AbstractStateCompositeProcessor createProcessor(HierarchicalConfiguration config) throws
            Exception;

    @Test
    public void testGhostProcessor() throws Exception {
    AbstractStateCompositeProcessor processor = null;

    try {
        processor = createProcessor(createConfig(Arrays.asList("root", "error", "ghost")));

        fail("ghost processor should not be allowed");
    } catch (ConfigurationException e) {
        // expected
    } finally {
        if (processor != null) {
        processor.dispose();
        }
    }

    }

    @Test
    public void testNoRootProcessor() throws Exception {
    AbstractStateCompositeProcessor processor = null;
    try {
        processor = createProcessor(createConfig(Arrays.asList("test", "error")));
        fail("root processor is needed");
    } catch (ConfigurationException e) {
        // expected
    } finally {
        if (processor != null) {
        processor.dispose();
        }
    }
    }

    @Test
    public void testNoErrorProcessor() throws Exception {
    AbstractStateCompositeProcessor processor = null;
    try {
        processor = createProcessor(createConfig(Arrays.asList("test", "root")));
        fail("error processor is needed");
    } catch (ConfigurationException e) {
        // expected
    } finally {
        if (processor != null) {
        processor.dispose();
        }
    }
    }

    private HierarchicalConfiguration createConfig(List<String> states) throws ConfigurationException {

    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\"?>");
    sb.append("<processors>");
        for (String state : states) {
            sb.append("<processor state=\"");
            sb.append(state);
            sb.append("\"/>");
        }
    sb.append("</processors>");

    DefaultConfigurationBuilder builder = new DefaultConfigurationBuilder();
    builder.load(new ByteArrayInputStream(sb.toString().getBytes()));
    return builder;
    }
}
