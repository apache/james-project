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

package org.apache.james.cli;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.apache.james.cli.domain.DomainCommand;
import org.apache.james.cli.mailbox.MailboxCommand;
import org.apache.james.cli.quota.QuotaCommand;
import org.apache.james.cli.user.UserCommand;
import org.apache.james.httpclient.FeignClientFactory;
import org.apache.james.httpclient.JwtToken;

import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import picocli.CommandLine;

@CommandLine.Command(
    name = "james-cli",
    description = "James Webadmin CLI")
public class WebAdminCli implements Callable<Integer> {

    public static final int CLI_FINISHED_SUCCEED = 0;
    public static final int CLI_FINISHED_FAILED = 1;

    public @CommandLine.Option(
        names = "--url",
        description = "James server URL",
        defaultValue = "http://127.0.0.1:8000")
    String jamesUrl;

    public @CommandLine.Option(
        names = "--jwt-token",
        description = "Authentication Token")
    String jwt;

    public @CommandLine.Option(
        names = "--jwt-from-file",
        description = "Authentication Token from a file")
    String jwtFilePath;

    @Override
    public Integer call() {
        return CLI_FINISHED_SUCCEED;
    }

    public static void main(String[] args) {
        PrintStream out = System.out;
        PrintStream err = System.err;
        int exitCode = execute(out, err, args);
        System.exit(exitCode);
    }

    public static int execute(PrintStream out, PrintStream err, String[] args) {
        WebAdminCli parent = new WebAdminCli();
        return new CommandLine(parent)
            .addSubcommand(new CommandLine.HelpCommand())
            .addSubcommand(new DomainCommand(out, parent, err))
            .addSubcommand(new UserCommand(out, parent, err))
            .addSubcommand(new MailboxCommand(out, parent, err))
            .addSubcommand(new QuotaCommand(out, parent, err))
            .execute(args);
    }

    public static int executeFluent(PrintStream out, PrintStream err, String... args) {
        return execute(out, err, args);
    }

    public static int executeFluent(PrintStream out, PrintStream err, Collection<String> args) {
        return execute(out, err, args.stream().toArray(String[]::new));
    }

    public Feign.Builder feignClientFactory(PrintStream err) {
        return new FeignClientFactory(new JwtToken(Optional.ofNullable(jwt),
            Optional.ofNullable(jwtFilePath), err))
            .builder()
            .decoder(new JacksonDecoder())
            .encoder(new JacksonEncoder());
    }

}