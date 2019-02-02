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

import java.util.List;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;


/**
 * <p>
 * Generates catalog and reports on mailets and matchers.
 * Aggregates mailets and matcher within subprojects
 * when run from the top level.
 * </p>
 * <h4>Notes</h4>
 * <ul>
 * <li>Should only used as a report.</li>
 * <li>Mailets are instantiated during report production. </li>
 * </ul>
 */
@Mojo(name = "aggregate", 
    requiresDependencyResolution = ResolutionScope.COMPILE,
    aggregator = true)
public class AggregateMailetdocsReport extends AbstractMailetdocsReport {

    @Parameter(defaultValue = "${reactorProjects}",
            readonly = true)
    private List<MavenProject> reactorProjects;

    /**
     * Build descriptors for mailets contained 
     * within subprojects.
     * @param project not null
     */
    @Override
    protected final List<MailetMatcherDescriptor> buildDescriptors(MavenProject project) {
        final DefaultDescriptorsExtractor extractor = new DefaultDescriptorsExtractor();
        if (project.isExecutionRoot()) {
            logProject(project);
            for (MavenProject subproject : reactorProjects) {
                logSubproject(subproject);
                extractor.extract(subproject, getLog());
            }
        } else {
            logNoSubprojects(project);
            extractor.extract(project, getLog());
        }
        return extractor.descriptors();
    }

    private void logProject(MavenProject project) {
        if (getLog().isDebugEnabled()) {
            getLog().debug("Aggregating mailets within " + project.getName());
        }
    }

    private void logSubproject(MavenProject subproject) {
        if (getLog().isDebugEnabled()) {
            getLog().debug("Adding descriptors in " + subproject.getName());
        }
    }

    private void logNoSubprojects(MavenProject project) {
        if (getLog().isDebugEnabled()) {
            getLog().debug("No subprojects for " + project.getName());
        }
    }
    
}
