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
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * <p>Runs MPT application.</p>
 * <p>Return values:</p>
 * <table>
 * <tr><td>0</td><td>Success</td></tr>
 * <tr><td>-1</td><td>Illegal Arguments</td></tr>
 * <tr><td>1</td><td>Script not found</td></tr>
 * <tr><td>1</td><td>Port not a number</td></tr>
 * </table>
 */
public class Main {

    
    private static final int FILE_NOT_FOUND = 1;
    private static final int PORT_NOT_A_NUMBER = 2;
    
    private static final char FILE_OPTION = 'f';
    private static final char PORT_OPTION = 'p';
    private static final char HOST_OPTION = 'h';
    private static final char SHABANG_OPTION = 's';
    private static final char VERBOSE_OPTION = 'v';

    public static final void main(final String[] args) throws Exception {
        final Options options = buildOptions();
        
        try {
            
            CommandLineParser parser = new GnuParser();
            CommandLine cmd = parser.parse(options, args);
            runCommand(cmd);
            
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            new HelpFormatter().printHelp( "mpt", options );
            System.exit(-1);
        }
        
    }

    private static void runCommand(CommandLine cmd) throws Exception {
        final boolean verbose = Boolean.parseBoolean(cmd.getOptionValue(VERBOSE_OPTION, Boolean.toString(false)));
        final File file = new File(cmd.getOptionValue(FILE_OPTION));
        if (file.exists()) {
            try {
                final int port = Integer.parseInt(cmd.getOptionValue(PORT_OPTION));    
                final String host = cmd.getOptionValue(HOST_OPTION, "localhost");
                final String shabang = cmd.getOptionValue(SHABANG_OPTION, null);
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

    @SuppressWarnings("static-access")
    private static Options buildOptions() {
        final Options options = new Options();
        
        addRunScriptOptions(options);
        
        return options;
    }

    @SuppressWarnings("static-access")
    private static void addRunScriptOptions(final Options options) {
        // -f <file> runs this script
        options.addOption(OptionBuilder
                    .withArgName("file")
                    .hasArg()
                    .withDescription("run this script")
                    .withLongOpt("file")
                    .isRequired()
                    .create(FILE_OPTION));
        
        // -p <port> runs against this port
        options.addOption(OptionBuilder
                    .withArgName("port")
                    .hasArg()
                    .withDescription("runs against this port")
                    .withLongOpt("port")
                    .isRequired()
                    .create(PORT_OPTION));
        
        // -h <host> runs against this host
        options.addOption(OptionBuilder
                    .withArgName("host")
                    .hasArg()
                    .withDescription("runs against this host (defaults to localhost)")
                    .withLongOpt("host")
                    .isRequired(false)
                    .create(HOST_OPTION));
        // -s <shabang> sets shabang
        options.addOption(OptionBuilder
                    .withArgName("shabang")
                    .hasArg()
                    .withDescription("sets shabang (defaults to empty)")
                    .withLongOpt("shabang")
                    .isRequired(false)
                    .create(SHABANG_OPTION));
        // -v sets logging to verbose
        options.addOption(OptionBuilder
                    .withDescription("prints lots of logging")
                    .withLongOpt("verbose")
                    .isRequired(false)
                    .create(VERBOSE_OPTION));
    }
}
