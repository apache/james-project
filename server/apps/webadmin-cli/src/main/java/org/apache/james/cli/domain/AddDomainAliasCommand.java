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

import static org.apache.james.httpclient.Constants.NO_CONTENT;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import org.apache.commons.io.IOUtils;
import org.apache.james.cli.WebAdminCli;
import org.apache.james.httpclient.DomainClient;

import feign.Response;
import picocli.CommandLine;

@CommandLine.Command(
    name = "addAlias",
    description = "Create a new domain alias")
public class AddDomainAliasCommand implements Callable<Integer> {
    @CommandLine.ParentCommand DomainCommand domainCommand;

    @CommandLine.Parameters(description = "Destination of the domain alias. This is the domain this alias belongs to.")
    String destinationDomain;

    @CommandLine.Parameters(description = "Source of the domain alias.")
    String sourceDomain;

    @Override
    public Integer call() {
        try {
            DomainClient domainClient = domainCommand.fullyQualifiedURL("/domains");
            Response rs = domainClient.addADomainAlias(destinationDomain, sourceDomain);
            if (rs.status() == NO_CONTENT) {
                return WebAdminCli.CLI_FINISHED_SUCCEED;
            } else {
                domainCommand.err.println(IOUtils.toString(rs.body().asInputStream(), StandardCharsets.UTF_8));
                return WebAdminCli.CLI_FINISHED_FAILED;
            }
        } catch (Exception e) {
            e.printStackTrace(domainCommand.err);
            return WebAdminCli.CLI_FINISHED_FAILED;
        }
    }

}