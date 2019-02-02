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

package org.apache.james.utils;

import static org.apache.james.utils.FileSystemFixture.CLASSPATH_FILE_SYSTEM;
import static org.apache.james.utils.FileSystemFixture.RECURSIVE_CLASSPATH_FILE_SYSTEM;
import static org.apache.james.utils.FileSystemFixture.THROWING_FILE_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.MessagingException;

import org.apache.james.transport.mailets.AddFooter;
import org.apache.james.transport.mailets.sub.TestMailet;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class GuiceMailetLoaderTest {

    public static final ImmutableSet<MailetConfigurationOverride> NO_MAILET_CONFIG_OVERRIDES = ImmutableSet.of();
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private Injector injector = Guice.createInjector();

    @Test
    public void getMailetShouldLoadClass() throws Exception {
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(injector,
            new ExtendedClassLoader(THROWING_FILE_SYSTEM),
            NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("AddFooter")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(mailet).isInstanceOf(AddFooter.class);
    }

    @Test
    public void getMailetShouldLoadClassWhenInSubPackageFromDefaultPackage() throws Exception {
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(injector,
            new ExtendedClassLoader(THROWING_FILE_SYSTEM),
            NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("sub.TestMailet")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(mailet).isInstanceOf(TestMailet.class);
    }

    @Test
    public void getMailetShouldThrowOnBadType() throws Exception {
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(injector,
            new ExtendedClassLoader(THROWING_FILE_SYSTEM),
            NO_MAILET_CONFIG_OVERRIDES);

        expectedException.expect(MessagingException.class);

        guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("org.apache.james.transport.matchers.SizeGreaterThan")
            .mailetContext(FakeMailContext.defaultContext())
            .build());
    }

    @Test
    public void getMailetShouldLoadClassWhenInExtensionsJars() throws Exception {
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(injector,
            new ExtendedClassLoader(CLASSPATH_FILE_SYSTEM),
            NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("CustomMailet")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(mailet.getClass().getCanonicalName())
            .isEqualTo("org.apache.james.transport.mailets.CustomMailet");
    }

    @Test
    public void getMailetShouldBrowseRecursivelyExtensionsJars() throws Exception {
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(injector,
            new ExtendedClassLoader(RECURSIVE_CLASSPATH_FILE_SYSTEM),
            NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("CustomMailet")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(mailet.getClass().getCanonicalName())
            .isEqualTo("org.apache.james.transport.mailets.CustomMailet");
    }

    @Test
    public void getMailedShouldAllowCustomPackages() throws Exception {
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(injector,
            new ExtendedClassLoader(CLASSPATH_FILE_SYSTEM),
            NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("com.custom.mailets.AnotherMailet")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(mailet.getClass().getCanonicalName())
            .isEqualTo("com.custom.mailets.AnotherMailet");
    }

    @Test
    public void getMailetShouldThrowOnUnknownMailet() throws Exception {
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(injector,
            new ExtendedClassLoader(CLASSPATH_FILE_SYSTEM),
            NO_MAILET_CONFIG_OVERRIDES);

        expectedException.expect(MessagingException.class);

        guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("org.apache.james.transport.mailets.Unknown")
            .mailetContext(FakeMailContext.defaultContext())
            .build());
    }

}
