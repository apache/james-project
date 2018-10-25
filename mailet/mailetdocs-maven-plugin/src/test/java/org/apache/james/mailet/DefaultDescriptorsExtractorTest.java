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

package org.apache.james.mailet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.james.mailet.MailetMatcherDescriptor.Type;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class DefaultDescriptorsExtractorTest {

    private MavenProject mavenProject;
    private Log log;
    private DefaultDescriptorsExtractor testee;

    @Before
    public void setup() {
        mavenProject = mock(MavenProject.class);
        log = mock(Log.class);
        testee = new DefaultDescriptorsExtractor();
    }

    @Test
    public void extractShouldSetExperimentalAttributeWhenScanningMailets() {
        when(mavenProject.getCompileSourceRoots())
            .thenReturn(ImmutableList.of("src/test/java/org/apache/james/mailet/experimental"));

        List<MailetMatcherDescriptor> descriptors = testee.extract(mavenProject, log)
            .descriptors();

        MailetMatcherDescriptor experimentalMailet = new MailetMatcherDescriptor();
        experimentalMailet.setFullyQualifiedName("org.apache.james.mailet.experimental.ExperimentalMailet");
        experimentalMailet.setName("ExperimentalMailet");
        experimentalMailet.setInfo(null);
        experimentalMailet.setType(Type.MAILET);
        experimentalMailet.setExperimental(true);
        MailetMatcherDescriptor nonExperimentalMailet = new MailetMatcherDescriptor();
        nonExperimentalMailet.setFullyQualifiedName("org.apache.james.mailet.experimental.NonExperimentalMailet");
        nonExperimentalMailet.setName("NonExperimentalMailet");
        nonExperimentalMailet.setInfo(null);
        nonExperimentalMailet.setType(Type.MAILET);
        nonExperimentalMailet.setExperimental(false);

        assertThat(descriptors).containsOnly(experimentalMailet, nonExperimentalMailet);
    }
    
    @Test
    public void extractShouldExcludeAnnotatedClassesWhenScanningMailets() {
        when(mavenProject.getCompileSourceRoots())
            .thenReturn(ImmutableList.of("src/test/java/org/apache/james/mailet/excluded"));

        List<MailetMatcherDescriptor> descriptors = testee.extract(mavenProject, log)
            .descriptors();

        MailetMatcherDescriptor notExcludedMailet = new MailetMatcherDescriptor();
        notExcludedMailet.setFullyQualifiedName("org.apache.james.mailet.excluded.NotExcludedFromDocumentationMailet");
        notExcludedMailet.setName("NotExcludedFromDocumentationMailet");
        notExcludedMailet.setInfo(null);
        notExcludedMailet.setType(Type.MAILET);
        notExcludedMailet.setExperimental(false);

        assertThat(descriptors).containsOnly(notExcludedMailet);
    }
}
