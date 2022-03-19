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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;

import jakarta.mail.MessagingException;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.MailAddress;
import org.apache.james.mailetcontainer.api.mock.ExceptionThrowingMailet;
import org.apache.james.mailetcontainer.api.mock.ExceptionThrowingMatcher;
import org.apache.james.mailetcontainer.api.mock.MockMailet;
import org.apache.james.mailetcontainer.api.mock.MockMatcher;
import org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessor.MailetProcessorListener;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.Matcher;
import org.junit.jupiter.api.Test;


public abstract class AbstractStateMailetProcessorTest {

    protected abstract AbstractStateMailetProcessor createProcessor(HierarchicalConfiguration<ImmutableNode> configuration) throws
            Exception;

    private HierarchicalConfiguration<ImmutableNode> createConfig(Class<?> matcherClass, Class<?> mailetClass, int count) throws
            ConfigurationException {
        StringBuilder sb = new StringBuilder();
        sb.append("<processor state=\"" + Mail.DEFAULT + "\">");
        sb.append("<mailet match=\"").append(matcherClass.getName()).append("=").append(count).append("\"").append(
                " class=\"").append(mailetClass.getName()).append("\">");
        sb.append("<state>test</state>");
        sb.append("</mailet>");

        sb.append("</processor>");

        return FileConfigurationProvider.getConfig(new ByteArrayInputStream(sb.toString().getBytes()));
    }

    @Test
    public void testSimpleRouting() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final MailImpl mail = newMail();

        AbstractStateMailetProcessor processor = createProcessor(createConfig(MockMatcher.class, MockMailet.class, 1));
        processor.addListener(new MailetProcessorListener() {

            @Override
            public void afterMatcher(Matcher m, String mailName, Collection<MailAddress> recipients,
                                     Collection<MailAddress> matches, long processTime, Throwable e) {
                if (MockMatcher.class.equals(m.getClass())) {
                    assertThat(mailName).isEqualTo(mail.getName());
                    // match one recipient
                    assertThat(matches.size()).isEqualTo(1);
                    assertThat(e).isNull();
                    latch.countDown();
                }

            }

            @Override
            public void afterMailet(Mailet m, String mailName, String state, long processTime, Throwable e) {
                // check for class name as the terminating  mailet will kick in too

                if (MockMailet.class.equals(m.getClass())) {
                    //assertEquals(mail.getName(), mailName);
                    assertThat(state).isEqualTo("test");
                    assertThat(e).isNull();
                    latch.countDown();
                }
            }
        });

        assertThat(mail.getState()).isEqualTo(Mail.DEFAULT);
        processor.service(mail);


        // the source mail should be ghosted as it reached the end of processor as only one recipient matched
        assertThat(mail.getState()).isEqualTo(Mail.GHOST);
        latch.await();
        processor.destroy();

    }

    @Test
    public void testSimpleRoutingMatchAll() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final MailImpl mail = newMail();

        AbstractStateMailetProcessor processor = createProcessor(createConfig(MockMatcher.class, MockMailet.class, 2));
        processor.addListener(new MailetProcessorListener() {

            @Override
            public void afterMatcher(Matcher m, String mailName, Collection<MailAddress> recipients,
                                     Collection<MailAddress> matches, long processTime, Throwable e) {
                if (MockMatcher.class.equals(m.getClass())) {
                    assertThat(mailName).isEqualTo(mail.getName());
                    // match all recipient
                    assertThat(matches.size()).isEqualTo(2);
                    assertThat(e).isNull();
                    latch.countDown();
                }

            }

            @Override
            public void afterMailet(Mailet m, String mailName, String state, long processTime, Throwable e) {
                // check for class name as the terminating  mailet will kick in too

                if (MockMailet.class.equals(m.getClass())) {
                    // the name should be the same as we have a full match
                    assertThat(mailName).isEqualTo(mail.getName());
                    assertThat(state).isEqualTo("test");
                    assertThat(e).isNull();
                    latch.countDown();
                }
            }
        });

        assertThat(mail.getState()).isEqualTo(Mail.DEFAULT);
        processor.service(mail);


        // the source mail should have the new state as it was a full match
        assertThat(mail.getState()).isEqualTo("test");
        latch.await();
        processor.destroy();

    }

    @Test
    public void matcherProcessingShouldNotResultInAnExceptionWhenMatcherThrows() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final MailImpl mail = newMail();

        AbstractStateMailetProcessor processor = createProcessor(createConfig(ExceptionThrowingMatcher.class,
                MockMailet.class, 0));
        processor.addListener(new MailetProcessorListener() {

            @Override
            public void afterMatcher(Matcher m, String mailName, Collection<MailAddress> recipients,
                                     Collection<MailAddress> matches, long processTime, Throwable e) {
                if (ExceptionThrowingMatcher.class.equals(m.getClass())) {
                    assertThat(mailName).isEqualTo(mail.getName());
                    // match no recipient because of the error
                    assertThat(matches).isNull();
                    assertThat(e).isNotNull();
                    latch.countDown();
                }

            }

            @Override
            public void afterMailet(Mailet m, String mailName, String state, long processTime, Throwable e) {
                throw new RuntimeException("Should not call any mailet!");
            }
        });

        assertThat(mail.getState()).isEqualTo(Mail.DEFAULT);

        processor.service(mail);

        // the source mail should have state error as the exception was thrown
        assertThat(mail.getState()).isEqualTo(Mail.ERROR);
        latch.await();
        processor.destroy();

    }

    @Test
    public void matcherProcessingShouldCaptureExceptionAsMailAttributeWhenMatcherThrows() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final MailImpl mail = newMail();

        AbstractStateMailetProcessor processor = createProcessor(createConfig(ExceptionThrowingMatcher.class,
                MockMailet.class, 0));
        processor.addListener(new MailetProcessorListener() {

            @Override
            public void afterMatcher(Matcher m, String mailName, Collection<MailAddress> recipients,
                                     Collection<MailAddress> matches, long processTime, Throwable e) {
                if (ExceptionThrowingMatcher.class.equals(m.getClass())) {
                    latch.countDown();
                }

            }

            @Override
            public void afterMailet(Mailet m, String mailName, String state, long processTime, Throwable e) {
                throw new RuntimeException("Should not call any mailet!");
            }
        });

        processor.service(mail);

        // the source mail should have captured the exception which was thrown
        assertThat(mail.getAttribute(Mail.MAILET_ERROR)).hasValueSatisfying(attribute ->
                assertThat(attribute.getValue().value().getClass()).isEqualTo(MessagingException.class));
        latch.await();
        processor.destroy();
    }

    @Test
    public void mailetProcessingShouldNotResultInAnExceptionWhenMailetThrows() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final MailImpl mail = newMail();

        AbstractStateMailetProcessor processor = createProcessor(createConfig(MockMatcher.class,
                ExceptionThrowingMailet.class, 1));
        processor.addListener(new MailetProcessorListener() {

            @Override
            public void afterMatcher(Matcher m, String mailName, Collection<MailAddress> recipients,
                                     Collection<MailAddress> matches, long processTime, Throwable e) {
                if (MockMatcher.class.equals(m.getClass())) {
                    assertThat(mailName).isEqualTo(mail.getName());
                    // match one recipient
                    assertThat(matches.size()).isEqualTo(1);
                    assertThat(e).isNull();
                    latch.countDown();
                }

            }

            @Override
            public void afterMailet(Mailet m, String mailName, String state, long processTime, Throwable e) {
                if (ExceptionThrowingMailet.class.equals(m.getClass())) {
                    // the name should be not the same as we have a part match
                    assertThat(mail.getName()).isNotEqualTo(mailName);
                    assertThat(e).isNotNull();
                    assertThat(state).isEqualTo(Mail.ERROR);
                    latch.countDown();
                }
            }
        });

        assertThat(mail.getState()).isEqualTo(Mail.DEFAULT);

        processor.service(mail);

        latch.await();
        processor.destroy();

    }

    private MailImpl newMail() throws MessagingException {
        return MailImpl.builder()
            .name(MailImpl.getId())
            .sender("test@localhost")
            .addRecipient("test@localhost")
            .addRecipient("test2@localhost")
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes("header: value\r\n".getBytes(UTF_8)))
            .build();
    }
}
