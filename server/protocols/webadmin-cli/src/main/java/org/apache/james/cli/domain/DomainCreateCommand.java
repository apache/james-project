/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.cli.domain;

import java.util.concurrent.Callable;

import org.apache.james.cli.WebAdminCli;
import org.apache.james.httpclient.DomainClient;

import feign.Feign;
import feign.Response;
import picocli.CommandLine;

@CommandLine.Command(
    name = "create",
    description = "Create a new domain")
public class DomainCreateCommand implements Callable<Integer> {

    public static final int CREATED_CODE = 204;

    @CommandLine.ParentCommand DomainCommand domainCommand;

    @CommandLine.Parameters
    String domainName;

    @Override
    public Integer call() {
        try {
            DomainClient domainClient = Feign.builder()
                .target(DomainClient.class, domainCommand.webAdminCli.jamesUrl + "/domains");
            Response rs = domainClient.createADomain(domainName);
            if (rs.status() == CREATED_CODE) {
                return WebAdminCli.CLI_FINISHED_SUCCEED;
            } else {
                return WebAdminCli.CLI_FINISHED_FAILED;
            }
        } catch (Exception e) {
            e.printStackTrace(domainCommand.err);
            return WebAdminCli.CLI_FINISHED_FAILED;
        }
    }

}