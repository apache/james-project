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

import static org.apache.james.filesystem.api.FileSystemFixture.CLASSPATH_FILE_SYSTEM;
import static org.apache.james.filesystem.api.FileSystemFixture.RECURSIVE_CLASSPATH_FILE_SYSTEM;
import static org.apache.james.filesystem.api.FileSystemFixture.THROWING_FILE_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import javax.mail.MessagingException;

import org.apache.james.transport.mailets.AddFooter;
import org.apache.james.transport.mailets.sub.TestMailet;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;

public class GuiceMailetLoaderTest {

    public static final ImmutableSet<MailetConfigurationOverride> NO_MAILET_CONFIG_OVERRIDES = ImmutableSet.of();
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void getMailetShouldLoadClass() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(THROWING_FILE_SYSTEM));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("AddFooter")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(mailet).isInstanceOf(AddFooter.class);
    }

    @Test
    public void getMailetShouldLoadClassWhenInSubPackageFromDefaultPackage() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(THROWING_FILE_SYSTEM));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("sub.TestMailet")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(mailet).isInstanceOf(TestMailet.class);
    }

    @Test
    public void getMailetShouldThrowOnBadType() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(THROWING_FILE_SYSTEM));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        expectedException.expect(MessagingException.class);

        guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("org.apache.james.transport.matchers.SizeGreaterThan")
            .mailetContext(FakeMailContext.defaultContext())
            .build());
    }

    @Test
    public void getMailetShouldLoadClassWhenInExtensionsJars() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(CLASSPATH_FILE_SYSTEM));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("CustomMailet")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(mailet.getClass().getCanonicalName())
            .isEqualTo("org.apache.james.transport.mailets.CustomMailet");
    }

    @Test
    public void getMailetShouldBrowseRecursivelyExtensionsJars() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(RECURSIVE_CLASSPATH_FILE_SYSTEM));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("CustomMailet")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(mailet.getClass().getCanonicalName())
            .isEqualTo("org.apache.james.transport.mailets.CustomMailet");
    }

    @Test
    public void getMailedShouldAllowCustomPackages() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(CLASSPATH_FILE_SYSTEM));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("com.custom.mailets.AnotherMailet")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(mailet.getClass().getCanonicalName())
            .isEqualTo("com.custom.mailets.AnotherMailet");
    }

    @Test
    public void getMailetShouldThrowOnUnknownMailet() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(CLASSPATH_FILE_SYSTEM));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        expectedException.expect(MessagingException.class);

        guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("org.apache.james.transport.mailets.Unknown")
            .mailetContext(FakeMailContext.defaultContext())
            .build());
    }

    @Test
    public void getMailetShouldLoadMailetsWithCustomDependencyInConstructor() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(RECURSIVE_CLASSPATH_FILE_SYSTEM));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("CustomMailetWithCustomDependencyInConstructor")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(mailet.getClass().getCanonicalName())
            .isEqualTo("org.apache.james.transport.mailets.CustomMailetWithCustomDependencyInConstructor");
    }

    @Test
    public void getMailetShouldLoadMailetsWithCustomDependencyInService() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(RECURSIVE_CLASSPATH_FILE_SYSTEM));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("CustomMailetWithCustomDependencyInService")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThatCode(() -> mailet.service(FakeMail.defaultFakeMail())).doesNotThrowAnyException();
    }

    @Test
    public void getMailetShouldAllowCustomInjections() throws Exception {
        GuiceGenericLoader genericLoader = new GuiceGenericLoader(
            Guice.createInjector(),
            new ExtendedClassLoader(RECURSIVE_CLASSPATH_FILE_SYSTEM),
            new ExtensionConfiguration(ImmutableList.of(new ClassName("org.apache.james.transport.mailets.MyExtensionModule"))));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("MyGenericMailet")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThatCode(() -> mailet.service(FakeMail.defaultFakeMail())).doesNotThrowAnyException();
    }
}
