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

import static org.apache.james.httpclient.Constants.NO_CONTENT;

import java.util.concurrent.Callable;

import org.apache.james.cli.WebAdminCli;
import org.apache.james.httpclient.QuotaClient;
import org.apache.james.util.Size;

import feign.Response;
import picocli.CommandLine;

@CommandLine.Command(
    name = "set",
    description = "Quota counts limit that applies for all users")
public class SetGlobalQuotaSizeCommand implements Callable<Integer> {
    @CommandLine.ParentCommand
    GlobalQuotaSizeCommand parentCommand;

    @CommandLine.Parameters
    String size;

    @Override
    public Integer call() {
        try {
            QuotaClient quotaClient = parentCommand.parentCommand.quotaCommand.fullyQualifiedURL();
            Response rs = quotaClient.setQuotaSize(Size.parse(size).asBytes());
            if (rs.status() == NO_CONTENT) {
                return WebAdminCli.CLI_FINISHED_SUCCEED;
            } else {
                return WebAdminCli.CLI_FINISHED_FAILED;
            }
        } catch (Exception e) {
            e.printStackTrace(parentCommand.parentCommand.quotaCommand.err);
            return WebAdminCli.CLI_FINISHED_FAILED;
        }
    }
}