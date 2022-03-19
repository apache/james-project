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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.MessagingException;

import org.apache.james.transport.mailets.AddFooter;
import org.apache.james.transport.mailets.sub.TestMailet;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;

class GuiceMailetLoaderTest {
    public static final ImmutableSet<MailetConfigurationOverride> NO_MAILET_CONFIG_OVERRIDES = ImmutableSet.of();

    @Test
    void getMailetShouldLoadClass() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(THROWING_FILE_SYSTEM));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("AddFooter")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(mailet).isInstanceOf(AddFooter.class);
    }

    @Test
    void getMailetShouldLoadClassWhenInSubPackageFromDefaultPackage() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(THROWING_FILE_SYSTEM));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("sub.TestMailet")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(mailet).isInstanceOf(TestMailet.class);
    }

    @Test
    void getMailetShouldThrowOnBadType() {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(THROWING_FILE_SYSTEM));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        assertThatThrownBy(() ->
            guiceMailetLoader.getMailet(FakeMailetConfig.builder()
                .mailetName("org.apache.james.transport.matchers.SizeGreaterThan")
                .mailetContext(FakeMailContext.defaultContext())
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getMailetShouldLoadClassWhenInExtensionsJars() throws Exception {
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
    void getMailetShouldBrowseRecursivelyExtensionsJars() throws Exception {
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
    void getMailedShouldAllowCustomPackages() throws Exception {
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
    void getMailetShouldThrowOnUnknownMailet() {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(CLASSPATH_FILE_SYSTEM));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        assertThatThrownBy(() ->
            guiceMailetLoader.getMailet(FakeMailetConfig.builder()
                .mailetName("org.apache.james.transport.mailets.Unknown")
                .mailetContext(FakeMailContext.defaultContext())
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getMailetShouldLoadMailetsWithCustomDependencyInConstructor() throws Exception {
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
    void getMailetShouldLoadMailetsWithCustomDependencyInService() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(RECURSIVE_CLASSPATH_FILE_SYSTEM));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("CustomMailetWithCustomDependencyInService")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThatCode(() -> mailet.service(FakeMail.defaultFakeMail())).doesNotThrowAnyException();
    }

    @Test
    void getMailetShouldAllowCustomInjections() throws Exception {
        GuiceGenericLoader genericLoader = new GuiceGenericLoader(
            Guice.createInjector(),
            new ExtendedClassLoader(RECURSIVE_CLASSPATH_FILE_SYSTEM),
            new ExtensionConfiguration(ImmutableList.of(new ClassName("org.apache.james.transport.mailets.MyExtensionModule")), ImmutableList.of()));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("MyGenericMailet")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThatCode(() -> mailet.service(FakeMail.defaultFakeMail())).doesNotThrowAnyException();
    }

    @Test
    void allMailetsShouldShareTheSameSingleton() throws Exception {
        GuiceGenericLoader genericLoader = new GuiceGenericLoader(
            Guice.createInjector(),
            new ExtendedClassLoader(RECURSIVE_CLASSPATH_FILE_SYSTEM),
            new ExtensionConfiguration(ImmutableList.of(new ClassName("org.apache.james.transport.mailets.MyExtensionModule")), ImmutableList.of()));
        GuiceMailetLoader guiceMailetLoader = new GuiceMailetLoader(genericLoader, NO_MAILET_CONFIG_OVERRIDES);

        Mailet mailet1 = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("MyGenericMailet")
            .mailetContext(FakeMailContext.defaultContext())
            .build());
        Mailet mailet2 = guiceMailetLoader.getMailet(FakeMailetConfig.builder()
            .mailetName("MyGenericMailet")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(mailet1).isEqualTo(mailet2);
    }
}
