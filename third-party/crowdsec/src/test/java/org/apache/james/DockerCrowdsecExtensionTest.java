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

package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.Container;

class DockerCrowdsecExtensionTest {
    @RegisterExtension
    static CrowdsecExtension crowdsecExtension = new CrowdsecExtension();

    @AfterEach
    public void tearDown() throws Exception {
        crowdsecExtension.getCrowdsecContainer().execInContainer("cscli", "decision", "delete", "--all");
    }

    @Test
    void addDecisionWithAnIPTest() throws IOException, InterruptedException {
        crowdsecExtension.getCrowdsecContainer().execInContainer("cscli" , "decision", "add", "-i", "192.168.0.4");
        Container.ExecResult result = crowdsecExtension.getCrowdsecContainer().execInContainer("cscli" , "decision", "list");
        assertThat(result.getStdout().contains("Ip:192.168.0.4") && result.getStdout().contains("manual 'ban' from 'localhost'")).isTrue();
    }

    @Test
    void addDecisionWithAnIPRangeTest() throws IOException, InterruptedException {
        crowdsecExtension.getCrowdsecContainer().execInContainer("cscli" , "decision", "add", "-r", "192.168.0.0/16");
        Container.ExecResult result = crowdsecExtension.getCrowdsecContainer().execInContainer("cscli" , "decision", "list");
        assertThat(result.getStdout().contains("Range:192.168.0.0/16") && result.getStdout().contains("manual 'ban' from 'localhost'")).isTrue();
    }

    @Test
    void deleteDecisionWithAnIPTest() throws IOException, InterruptedException {
        crowdsecExtension.getCrowdsecContainer().execInContainer("cscli" , "decision", "add", "-i", "192.168.0.4");
        Container.ExecResult result = crowdsecExtension.getCrowdsecContainer().execInContainer("cscli" , "decision", "list");
        assertThat(result.getStdout().contains("Ip:192.168.0.4") && result.getStdout().contains("manual 'ban' from 'localhost'")).isTrue();

        crowdsecExtension.getCrowdsecContainer().execInContainer("cscli" , "decision", "delete", "-i", "192.168.0.4");
        result = crowdsecExtension.getCrowdsecContainer().execInContainer("cscli" , "decision", "list");
        assertThat(result.getStdout()).contains("No active decisions");
    }

    @Test
    void deleteDecisionWithAnIPRangeTest() throws IOException, InterruptedException {
        crowdsecExtension.getCrowdsecContainer().execInContainer("cscli" , "decision", "add", "-r", "192.168.0.0/16");
        Container.ExecResult result = crowdsecExtension.getCrowdsecContainer().execInContainer("cscli" , "decision", "list");
        assertThat(result.getStdout().contains("Range:192.168.0.0/16") && result.getStdout().contains("manual 'ban' from 'localhost'")).isTrue();

        crowdsecExtension.getCrowdsecContainer().execInContainer("cscli" , "decision", "delete", "-r", "192.168.0.0/16");
        result = crowdsecExtension.getCrowdsecContainer().execInContainer("cscli" , "decision", "list");
        assertThat(result.getStdout()).contains("No active decisions");
    }
}
