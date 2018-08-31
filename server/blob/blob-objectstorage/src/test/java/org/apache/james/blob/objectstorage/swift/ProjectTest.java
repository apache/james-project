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

package org.apache.james.blob.objectstorage.swift;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class ProjectTest {

    private static final ProjectName PROJECT_NAME = ProjectName.of("project");
    private static final DomainName DOMAIN_NAME = DomainName.of("domainName");
    private static final DomainId DOMAIN_ID = DomainId.of("domainId");

    @Test
    public void projectShouldRespectBeanContract() {
        EqualsVerifier.forClass(Project.class).verify();
    }

    @Test
    void projectCanBeBuiltFromNameAlone() {
        Project project = Project.of(PROJECT_NAME);
        assertThat(project.domainName()).isEmpty();
        assertThat(project.domainId()).isEmpty();
        assertThat(project.name()).isEqualTo(PROJECT_NAME);
    }


    @Test
    void projectCanBeBuiltFromNameAnDomainName() {
        Project project = Project.of(PROJECT_NAME, DOMAIN_NAME);
        assertThat(project.domainName()).contains(DOMAIN_NAME);
        assertThat(project.domainId()).isEmpty();
        assertThat(project.name()).isEqualTo(PROJECT_NAME);
    }

    @Test
    void projectCanBeBuiltFromNameAnDomainId() {
        Project project = Project.of(PROJECT_NAME, DOMAIN_ID);
        assertThat(project.domainName()).isEmpty();
        assertThat(project.domainId()).contains(DOMAIN_ID);
        assertThat(project.name()).isEqualTo(PROJECT_NAME);
    }
}