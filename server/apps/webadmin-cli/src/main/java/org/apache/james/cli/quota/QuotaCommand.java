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

import java.io.PrintStream;
import java.util.concurrent.Callable;

import org.apache.james.cli.WebAdminCli;
import org.apache.james.webadmin.httpclient.QuotaClient;
import org.apache.james.webadmin.httpclient.feign.QuotaFeignClient;

import picocli.CommandLine;

@CommandLine.Command(
        name = "quota",
        description = "Manage Quotas",
        subcommands = {
            GlobalQuotaCommand.class
        })
public class QuotaCommand implements Callable<Integer> {

    protected final WebAdminCli webAdminCli;
    protected final PrintStream out;
    protected final PrintStream err;

    public QuotaCommand(PrintStream out, WebAdminCli webAdminCli, PrintStream err) {
        this.out = out;
        this.webAdminCli = webAdminCli;
        this.err = err;
    }

    @Override
    public Integer call() {
        return WebAdminCli.CLI_FINISHED_SUCCEED;
    }

    public QuotaClient fullyQualifiedURL() {
        return new QuotaClient(webAdminCli.feignClientFactory(err).target(QuotaFeignClient.class, webAdminCli.jamesUrl));
    }
}