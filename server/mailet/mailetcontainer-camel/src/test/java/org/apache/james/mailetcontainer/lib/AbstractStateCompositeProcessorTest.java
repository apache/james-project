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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.api.mock.MockMailProcessor;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.mailet.Mail;
import org.junit.jupiter.api.Test;

public abstract class AbstractStateCompositeProcessorTest {
    @Test
    public void testChooseRightProcessor() throws Exception {
        AbstractStateCompositeProcessor processor = new AbstractStateCompositeProcessor() {

            @Override
            protected MailProcessor createMailProcessor(String state, HierarchicalConfiguration<ImmutableNode> config) {
                return new MockMailProcessor("") {
                    @Override
                    public void service(Mail mail) {
                        // check if the right processor wasAbstractStateMailetProcessorTest selected depending on the state
                        assertThat(mail.getState()).isEqualTo(state);
                        super.service(mail);
                    }
                };
            }
        };
        processor.configure(createConfig(Arrays.asList("root", "error", "test")));
        processor.init();

        try {
            Mail mail1 = MailImpl.builder().name("mail1").state(Mail.DEFAULT).build();
            Mail mail2 = MailImpl.builder().name("mail2").state(Mail.ERROR).build();
            Mail mail3 = MailImpl.builder().name("mail3").state("test").build();
            Mail mail4 = MailImpl.builder().name("mail4").state("invalid").build();

            processor.service(mail1);
            processor.service(mail2);
            processor.service(mail3);

            processor.service(mail4);
        } finally {
            processor.dispose();
        }

    }

    protected abstract AbstractStateCompositeProcessor createProcessor(HierarchicalConfiguration<ImmutableNode> config) throws
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

    private HierarchicalConfiguration<ImmutableNode> createConfig(List<String> states) throws ConfigurationException, IOException {

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?>");
        sb.append("<processors>");
            for (String state : states) {
                sb.append("<processor state=\"");
                sb.append(state);
                sb.append("\"/>");
            }
        sb.append("</processors>");

        return FileConfigurationProvider.getConfig(new ByteArrayInputStream(sb.toString().getBytes()));
    }
}
