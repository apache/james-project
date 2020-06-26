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

package org.apache.james.mpt.app;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.james.util.Port;

/**
 * <p>Runs MPT application.</p>
 * <p>Return values:</p>
 * <table>
 *  <caption>Mail Performance Test framework</caption>
 * <tr><td>0</td><td>Success</td></tr>
 * <tr><td>-1</td><td>Illegal Arguments</td></tr>
 * <tr><td>1</td><td>Script not found</td></tr>
 * <tr><td>1</td><td>Port not a number</td></tr>
 * </table>
 */
public class Main {

    
    private static final int FILE_NOT_FOUND = 1;
    private static final int PORT_NOT_A_NUMBER = 2;
    
    private static final String FILE_OPTION = "f";
    private static final String PORT_OPTION = "p";
    private static final String HOST_OPTION = "h";
    private static final String SHABANG_OPTION = "s";
    private static final String VERBOSE_OPTION = "v";

    public static void main(String[] args) throws Exception {
        Options options = buildOptions();
        
        try {
            
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);
            runCommand(cmd);
            
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            new HelpFormatter().printHelp("mpt", options);
            System.exit(-1);
        }
        
    }

    private static void runCommand(CommandLine cmd) throws Exception {
        boolean verbose = Boolean.parseBoolean(cmd.getOptionValue(VERBOSE_OPTION, Boolean.toString(false)));
        File file = new File(cmd.getOptionValue(FILE_OPTION));
        if (file.exists()) {
            try {
                Port port = new Port(Integer.parseInt(cmd.getOptionValue(PORT_OPTION)));    
                String host = cmd.getOptionValue(HOST_OPTION, "localhost");
                String shabang = cmd.getOptionValue(SHABANG_OPTION, null);
                RunScript runner = new RunScript(file, port, host, shabang, verbose);
                runner.run();
                
            } catch (NumberFormatException e) {
                System.out.println("Port must be numeric");
                System.exit(PORT_NOT_A_NUMBER);
            }
        } else {
            System.out.println("Script not found");
            System.exit(FILE_NOT_FOUND);
        }
    }

    private static Options buildOptions() {
        Options options = new Options();
        
        addRunScriptOptions(options);
        
        return options;
    }

    private static void addRunScriptOptions(Options options) {
        // -f <file> runs this script
        options.addOption(Option.builder(FILE_OPTION)
                    .argName("file")
                    .hasArg()
                    .desc("run this script")
                    .longOpt("file")
                    .required()
                    .build());
        
        // -p <port> runs against this port
        options.addOption(Option.builder(PORT_OPTION)
                    .argName("port")
                    .hasArg()
                    .desc("runs against this port")
                    .longOpt("port")
                    .required()
                    .build());
        
        // -h <host> runs against this host
        options.addOption(Option.builder(HOST_OPTION)
                    .argName("host")
                    .hasArg()
                    .desc("runs against this host (defaults to localhost)")
                    .longOpt("host")
                    .required(false)
                    .build());
        // -s <shabang> sets shabang
        options.addOption(Option.builder(SHABANG_OPTION)
                    .argName("shabang")
                    .hasArg()
                    .desc("sets shabang (defaults to empty)")
                    .longOpt("shabang")
                    .required(false)
                    .build());
        // -v sets logging to verbose
        options.addOption(Option.builder(VERBOSE_OPTION)
                    .desc("prints lots of logging")
                    .longOpt("verbose")
                    .required(false)
                    .build());
    }
}
