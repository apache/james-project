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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.core.MailAddress;
import org.apache.james.mailetcontainer.api.mock.ExceptionThrowingMailet;
import org.apache.james.mailetcontainer.api.mock.ExceptionThrowingMatcher;
import org.apache.james.mailetcontainer.api.mock.MockMailet;
import org.apache.james.mailetcontainer.api.mock.MockMatcher;
import org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessor.MailetProcessorListener;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.Matcher;
import org.junit.Test;

public abstract class AbstractStateMailetProcessorTest {

    protected abstract AbstractStateMailetProcessor createProcessor(HierarchicalConfiguration configuration) throws
            Exception;

    private HierarchicalConfiguration createConfig(Class<?> matcherClass, Class<?> mailetClass, int count) throws
            ConfigurationException {
        StringBuilder sb = new StringBuilder();
        sb.append("<processor state=\"" + Mail.DEFAULT + "\">");
        sb.append("<mailet match=\"").append(matcherClass.getName()).append("=").append(count).append("\"").append(
                " class=\"").append(mailetClass.getName()).append("\">");
        sb.append("<state>test</state>");
        sb.append("</mailet>");

        sb.append("</processor>");

        DefaultConfigurationBuilder builder = new DefaultConfigurationBuilder();
        builder.load(new ByteArrayInputStream(sb.toString().getBytes()));
        return builder;
    }

    @Test
    public void testSimpleRouting() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final MailImpl mail = new MailImpl();
        mail.setName(MailImpl.getId());
        mail.setSender(new MailAddress("test@localhost"));
        mail.setRecipients(Arrays.asList(new MailAddress("test@localhost"), new MailAddress("test2@localhost")));

        AbstractStateMailetProcessor processor = createProcessor(createConfig(MockMatcher.class, MockMailet.class, 1));
        processor.addListener(new MailetProcessorListener() {

            @Override
            public void afterMatcher(Matcher m, String mailName, Collection<MailAddress> recipients,
                                     Collection<MailAddress> matches, long processTime, Exception e) {
                if (MockMatcher.class.equals(m.getClass())) {
                    assertEquals(mail.getName(), mailName);
                    // match one recipient
                    assertEquals(1, matches.size());
                    assertNull(e);
                    latch.countDown();
                }

            }

            @Override
            public void afterMailet(Mailet m, String mailName, String state, long processTime, Exception e) {
                // check for class name as the terminating  mailet will kick in too

                if (MockMailet.class.equals(m.getClass())) {
                    //assertEquals(mail.getName(), mailName);
                    assertEquals("test", state);
                    assertNull(e);
                    latch.countDown();
                }
            }
        });

        assertEquals(Mail.DEFAULT, mail.getState());
        processor.service(mail);


        // the source mail should be ghosted as it reached the end of processor as only one recipient matched
        assertEquals(Mail.GHOST, mail.getState());
        latch.await();
        processor.destroy();

    }

    @Test
    public void testSimpleRoutingMatchAll() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final MailImpl mail = new MailImpl();
        mail.setName(MailImpl.getId());
        mail.setSender(new MailAddress("test@localhost"));
        mail.setRecipients(Arrays.asList(new MailAddress("test@localhost"), new MailAddress("test2@localhost")));

        AbstractStateMailetProcessor processor = createProcessor(createConfig(MockMatcher.class, MockMailet.class, 2));
        processor.addListener(new MailetProcessorListener() {

            @Override
            public void afterMatcher(Matcher m, String mailName, Collection<MailAddress> recipients,
                                     Collection<MailAddress> matches, long processTime, Exception e) {
                if (MockMatcher.class.equals(m.getClass())) {
                    assertEquals(mail.getName(), mailName);
                    // match all recipient
                    assertEquals(2, matches.size());
                    assertNull(e);
                    latch.countDown();
                }

            }

            @Override
            public void afterMailet(Mailet m, String mailName, String state, long processTime, Exception e) {
                // check for class name as the terminating  mailet will kick in too

                if (MockMailet.class.equals(m.getClass())) {
                    // the name should be the same as we have a full match
                    assertEquals(mail.getName(), mailName);
                    assertEquals("test", state);
                    assertNull(e);
                    latch.countDown();
                }
            }
        });

        assertEquals(Mail.DEFAULT, mail.getState());
        processor.service(mail);


        // the source mail should have the new state as it was a full match
        assertEquals("test", mail.getState());
        latch.await();
        processor.destroy();

    }

    @Test
    public void matcherProcessingShouldNotResultInAnExceptionWhenMatcherThrows() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final MailImpl mail = new MailImpl();
        mail.setName(MailImpl.getId());
        mail.setSender(new MailAddress("test@localhost"));
        mail.setRecipients(Arrays.asList(new MailAddress("test@localhost"), new MailAddress("test2@localhost")));

        AbstractStateMailetProcessor processor = createProcessor(createConfig(ExceptionThrowingMatcher.class,
                MockMailet.class, 0));
        processor.addListener(new MailetProcessorListener() {

            @Override
            public void afterMatcher(Matcher m, String mailName, Collection<MailAddress> recipients,
                                     Collection<MailAddress> matches, long processTime, Exception e) {
                if (ExceptionThrowingMatcher.class.equals(m.getClass())) {
                    assertEquals(mail.getName(), mailName);
                    // match no recipient because of the error
                    assertNull(matches);
                    assertNotNull(e);
                    latch.countDown();
                }

            }

            @Override
            public void afterMailet(Mailet m, String mailName, String state, long processTime, Exception e) {
                throw new RuntimeException("Should not call any mailet!");
            }
        });

        assertEquals(Mail.DEFAULT, mail.getState());

        processor.service(mail);

        // the source mail should have state error as the exception was thrown
        assertEquals(Mail.ERROR, mail.getState());
        latch.await();
        processor.destroy();

    }

    @Test
    public void mailetProcessingShouldNotResultInAnExceptionWhenMailetThrows() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final MailImpl mail = new MailImpl();
        mail.setName(MailImpl.getId());
        mail.setSender(new MailAddress("test@localhost"));
        mail.setRecipients(Arrays.asList(new MailAddress("test@localhost"), new MailAddress("test2@localhost")));

        AbstractStateMailetProcessor processor = createProcessor(createConfig(MockMatcher.class,
                ExceptionThrowingMailet.class, 1));
        processor.addListener(new MailetProcessorListener() {

            @Override
            public void afterMatcher(Matcher m, String mailName, Collection<MailAddress> recipients,
                                     Collection<MailAddress> matches, long processTime, Exception e) {
                if (MockMatcher.class.equals(m.getClass())) {
                    assertEquals(mail.getName(), mailName);
                    // match one recipient
                    assertEquals(1, matches.size());
                    assertNull(e);
                    latch.countDown();
                }

            }

            @Override
            public void afterMailet(Mailet m, String mailName, String state, long processTime, Exception e) {
                if (ExceptionThrowingMailet.class.equals(m.getClass())) {
                    // the name should be not the same as we have a part match
                    assertFalse(mail.getName().equals(mailName));
                    assertNotNull(e);
                    assertEquals(Mail.ERROR, state);
                    latch.countDown();
                }
            }
        });

        assertEquals(Mail.DEFAULT, mail.getState());

        processor.service(mail);

        latch.await();
        processor.destroy();

    }
}
