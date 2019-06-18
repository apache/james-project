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
package org.apache.james.mpt.imapmailbox.external.james.host.docker;

import java.io.IOException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.mpt.imapmailbox.external.james.ProvisioningException;
import org.apache.james.mpt.imapmailbox.external.james.host.ProvisioningAPI;
import org.apache.james.util.docker.DockerContainer;
import org.testcontainers.containers.Container;

import com.google.common.collect.ImmutableList;

public class CliProvisioningAPI implements ProvisioningAPI {

    public enum CliType {
        JAR,
        SH
    }

    private final DockerContainer container;
    private final String[] cmd;

    private static final String[] jarCmd = {"java", "-jar", "/root/james-cli.jar"};
    private static final String[] hostAndPort = {"-h", "127.0.0.1", "-p", "9999"};

    public CliProvisioningAPI(CliType cliType, DockerContainer container) throws InterruptedException, ProvisioningException, IOException, IllegalArgumentException {
        this.container = container;
        switch (cliType) {
            case JAR:
                cmd = jarCmd;
                break;
            case SH:
                cmd = shCmd();
                break;
            default:
                throw new IllegalArgumentException("UNKNOWN CliType");
        }
    }

    @Override
    public void addDomain(String domain) throws Exception {
        Container.ExecResult execResult = exec("adddomain", domain);

        if (execResult.getExitCode() != 0) {
            throw new ProvisioningException("Failed to add domain" + executionResultToString(execResult));
        }
    }

    @Override
    public void addUser(String user, String password) throws Exception {
        Container.ExecResult execResult = exec("adduser", user, password);

        if (execResult.getExitCode() != 0) {
            throw new ProvisioningException("Failed to add user" + executionResultToString(execResult));
        }
    }

    private String[] shCmd() throws IOException, InterruptedException, ProvisioningException {
        Container.ExecResult findCli = container.exec("find", "/root", "-name", "james-cli.sh");
        if (findCli.getExitCode() != 0) {
            throw new ProvisioningException("Failed to getCliPath" + executionResultToString(findCli));
        }
        return new String[]{findCli.getStdout().trim()};
    }


    private Container.ExecResult exec(String... commands) throws Exception {
        String[] command = ArrayUtils.addAll(ArrayUtils.addAll(cmd, hostAndPort), commands);
        return container.exec(command);
    }

    private String executionResultToString(Container.ExecResult execResult) {
        return StringUtils.join(ImmutableList.of(execResult.getStdout(), execResult.getStderr()), " ");
    }

}
