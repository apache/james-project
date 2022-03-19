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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.MessagingException;

import org.apache.james.transport.matchers.All;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.Test;

class GuiceMatcherLoaderTest {
    @Test
    void getMatcherShouldLoadClass() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(THROWING_FILE_SYSTEM));
        GuiceMatcherLoader guiceMailetLoader = new GuiceMatcherLoader(genericLoader);

        Matcher matcher = guiceMailetLoader.getMatcher(FakeMatcherConfig.builder()
            .matcherName("All")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(matcher).isInstanceOf(All.class);
    }

    @Test
    void getMatcherShouldLoadClassWhenInSubPackageFromDefaultPackage() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(THROWING_FILE_SYSTEM));
        GuiceMatcherLoader guiceMailetLoader = new GuiceMatcherLoader(genericLoader);

        Matcher matcher = guiceMailetLoader.getMatcher(FakeMatcherConfig.builder()
            .matcherName("sub.TestMatcher")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(matcher).isInstanceOf(All.class);
    }

    @Test
    void getMatcherShouldThrowOnBadType() {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(THROWING_FILE_SYSTEM));
        GuiceMatcherLoader guiceMatcherLoader = new GuiceMatcherLoader(genericLoader);

        assertThatThrownBy(() ->
            guiceMatcherLoader.getMatcher(FakeMatcherConfig.builder()
                .matcherName("org.apache.james.transport.mailets.Null")
                .mailetContext(FakeMailContext.defaultContext())
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getMatcherShouldLoadClassWhenInExtensionsJars() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(CLASSPATH_FILE_SYSTEM));
        GuiceMatcherLoader guiceMatcherLoader = new GuiceMatcherLoader(genericLoader);

        Matcher matcher = guiceMatcherLoader.getMatcher(FakeMatcherConfig.builder()
            .matcherName("CustomMatcher")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(matcher.getClass().getCanonicalName())
            .isEqualTo("org.apache.james.transport.matchers.CustomMatcher");
    }

    @Test
    void getMatcherShouldBrowseRecursivelyExtensionJars() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(RECURSIVE_CLASSPATH_FILE_SYSTEM));
        GuiceMatcherLoader guiceMatcherLoader = new GuiceMatcherLoader(genericLoader);

        Matcher matcher = guiceMatcherLoader.getMatcher(FakeMatcherConfig.builder()
            .matcherName("CustomMatcher")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(matcher.getClass().getCanonicalName())
            .isEqualTo("org.apache.james.transport.matchers.CustomMatcher");
    }

    @Test
    void getMatcherShouldAllowCustomPackages() throws Exception {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(RECURSIVE_CLASSPATH_FILE_SYSTEM));
        GuiceMatcherLoader guiceMatcherLoader = new GuiceMatcherLoader(genericLoader);

        Matcher matcher = guiceMatcherLoader.getMatcher(FakeMatcherConfig.builder()
            .matcherName("com.custom.matchers.AnotherMatcher")
            .mailetContext(FakeMailContext.defaultContext())
            .build());

        assertThat(matcher.getClass().getCanonicalName())
            .isEqualTo("com.custom.matchers.AnotherMatcher");
    }

    @Test
    void getMatcherShouldThrowOnUnknownMailet() {
        GuiceGenericLoader genericLoader = GuiceGenericLoader.forTesting(new ExtendedClassLoader(CLASSPATH_FILE_SYSTEM));
        GuiceMatcherLoader guiceMatcherLoader = new GuiceMatcherLoader(genericLoader);

        assertThatThrownBy(() ->
            guiceMatcherLoader.getMatcher(FakeMatcherConfig.builder()
                .matcherName("org.apache.james.transport.matchers.Unknown")
                .mailetContext(FakeMailContext.defaultContext())
                .build()))
            .isInstanceOf(MessagingException.class);
    }

}
