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

package org.apache.james.cli.quota;

import java.util.Optional;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.apache.james.cli.WebAdminCli;
import org.apache.james.httpclient.QuotaClient;

import picocli.CommandLine;

@CommandLine.Command(
    name = "get",
    description = "Get quota sizes limit that applies for all users")
public class GetGlobalQuotaSizeCommand implements Callable<Integer> {
    @CommandLine.ParentCommand
    GlobalQuotaSizeCommand parentCommand;

    @Override
    public Integer call() {
        try {
            QuotaClient quotaClient = parentCommand.parentCommand.quotaCommand.fullyQualifiedURL();
            String message = Optional.ofNullable(quotaClient.getQuotaSize())
                .map(FileUtils::byteCountToDisplaySize)
                .orElse("No global quota defined");
            parentCommand.parentCommand.quotaCommand.out.println(message);
            return WebAdminCli.CLI_FINISHED_SUCCEED;
        } catch (Exception e) {
            e.printStackTrace(parentCommand.parentCommand.quotaCommand.err);
            return WebAdminCli.CLI_FINISHED_FAILED;
        }
    }
}