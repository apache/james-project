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
package org.apache.james.cli;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.time.StopWatch;
import org.apache.james.adapter.mailbox.SerializableQuota;
import org.apache.james.cli.exceptions.InvalidArgumentNumberException;
import org.apache.james.cli.exceptions.InvalidPortException;
import org.apache.james.cli.exceptions.JamesCliException;
import org.apache.james.cli.exceptions.MissingCommandException;
import org.apache.james.cli.exceptions.UnrecognizedCommandException;
import org.apache.james.cli.probe.ServerProbe;
import org.apache.james.cli.probe.impl.JmxServerProbe;
import org.apache.james.cli.type.CmdType;
import org.apache.james.cli.utils.ValueWithUnit;
import org.apache.james.mailbox.model.Quota;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Command line utility for managing various aspect of the James server.
 */
public class ServerCmd {
    public static final String HOST_OPT_LONG = "host";
    public static final String HOST_OPT_SHORT = "h";
    public static final String PORT_OPT_LONG = "port";
    public static final String PORT_OPT_SHORT = "p";

    private static final int DEFAULT_PORT = 9999;

    private static Options createOptions() {
        Options options = new Options();
        Option optHost = new Option(HOST_OPT_SHORT, HOST_OPT_LONG, true, "node hostname or ip address");
        optHost.setRequired(true);
        options.addOption(optHost);
        options.addOption(PORT_OPT_SHORT, PORT_OPT_LONG, true, "remote jmx agent port number");
        return options;
    }

    private final ServerProbe probe;

    public ServerCmd(ServerProbe probe) {
        this.probe = probe;
    }

    /**
     * Main method to initialize the class.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {

        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            CommandLine cmd = parseCommandLine(args);
            CmdType cmdType =new ServerCmd(new JmxServerProbe(cmd.getOptionValue(HOST_OPT_LONG), getPort(cmd)))
                .executeCommandLine(cmd);
            stopWatch.split();
            print(new String[] { Joiner.on(' ')
                    .join(cmdType.getCommand(), "command executed sucessfully in", stopWatch.getSplitTime(), "ms.")},
                System.out);
            stopWatch.stop();
            System.exit(0);
        } catch (JamesCliException e) {
            failWithMessage(e.getMessage());
        } catch (ParseException e) {
            failWithMessage("Error parsing command line : " + e.getMessage());
        } catch (IOException ioe) {
            failWithMessage("Error connecting to remote JMX agent : " + ioe.getMessage());
        } catch (Exception e) {
            failWithMessage("Error while executing command:" + e.getMessage());
        }

    }

    @VisibleForTesting
    static CommandLine parseCommandLine(String[] args) throws ParseException {
        CommandLineParser parser = new PosixParser();
        CommandLine commandLine = parser.parse(createOptions(), args);
        if (commandLine.getArgs().length < 1) {
            throw new MissingCommandException();
        }
        return commandLine;
    }

    @VisibleForTesting
    static int getPort(CommandLine cmd) throws ParseException {
        String portNum = cmd.getOptionValue(PORT_OPT_LONG);
        if (portNum != null) {
            try {
                return validatePortNumber(Integer.parseInt(portNum));
            } catch (NumberFormatException e) {
                throw new ParseException("Port must be a number");
            }
        }
        return DEFAULT_PORT;
    }

    private static int validatePortNumber(int portNumber) {
        if (portNumber < 1 || portNumber > 65535) {
            throw new InvalidPortException(portNumber);
        }
        return portNumber;
    }

    private static void failWithMessage(String s) {
        System.err.println(s);
        printUsage();
        System.exit(1);
    }

    @VisibleForTesting
    CmdType executeCommandLine(CommandLine cmd) throws Exception {
        String[] arguments = cmd.getArgs();
        String cmdName = arguments[0];
        CmdType cmdType = CmdType.lookup(cmdName);
        if (cmdType == null) {
            throw  new UnrecognizedCommandException(cmdName);
        }
        if (! cmdType.hasCorrectArguments(arguments.length)) {
            throw new InvalidArgumentNumberException(cmdType, arguments.length);
        }
        executeCommand(arguments, cmdType);
        return cmdType;
    }

    private void executeCommand(String[] arguments, CmdType cmdType) throws Exception {
        switch (cmdType) {
        case ADDUSER:
            probe.addUser(arguments[1], arguments[2]);
            break;
        case REMOVEUSER:
            probe.removeUser(arguments[1]);
            break;
        case LISTUSERS:
            print(probe.listUsers(), System.out);
            break;
        case ADDDOMAIN:
            probe.addDomain(arguments[1]);
            break;
        case REMOVEDOMAIN:
            probe.removeDomain(arguments[1]);
            break;
        case CONTAINSDOMAIN:
            if (probe.containsDomain(arguments[1])) {
                System.out.println(arguments[1] + " exists");
            } else {
                System.out.println(arguments[1] + " does not exists");
            }
            break;
        case LISTDOMAINS:
            print(probe.listDomains(), System.out);
            break;
        case LISTMAPPINGS:
            print(probe.listMappings(), System.out);
            break;
        case LISTUSERDOMAINMAPPINGS:
            Collection<String> userDomainMappings = probe.listUserDomainMappings(arguments[1], arguments[2]);
            print(userDomainMappings.toArray(new String[0]), System.out);
            break;
        case ADDADDRESSMAPPING:
            probe.addAddressMapping(arguments[1], arguments[2], arguments[3]);
            break;
        case REMOVEADDRESSMAPPING:
            probe.removeAddressMapping(arguments[1], arguments[2], arguments[3]);
            break;
        case ADDREGEXMAPPING:
            probe.addRegexMapping(arguments[1], arguments[2], arguments[3]);
            break;
        case REMOVEREGEXMAPPING:
            probe.removeRegexMapping(arguments[1], arguments[2], arguments[3]);
            break;
        case SETPASSWORD:
            probe.setPassword(arguments[1], arguments[2]);
            break;
        case COPYMAILBOX:
            probe.copyMailbox(arguments[1], arguments[2]);
            break;
        case DELETEUSERMAILBOXES:
            probe.deleteUserMailboxesNames(arguments[1]);
            break;
        case CREATEMAILBOX:
            probe.createMailbox(arguments[1], arguments[2], arguments[3]);
            break;
        case LISTUSERMAILBOXES:
            Collection<String> mailboxes = probe.listUserMailboxes(arguments[1]);
            print(mailboxes.toArray(new String[0]), System.out);
            break;
        case DELETEMAILBOX:
            probe.deleteMailbox(arguments[1], arguments[2], arguments[3]);
            break;
        case GETSTORAGEQUOTA:
            printStorageQuota(arguments[1], probe.getStorageQuota(arguments[1]));
            break;
        case GETMESSAGECOUNTQUOTA:
            printMessageQuota(arguments[1], probe.getMessageCountQuota(arguments[1]));
            break;
        case GETQUOTAROOT:
            System.out.println("Quota Root : " + probe.getQuotaRoot(arguments[1], arguments[2], arguments[3]));
            break;
        case GETMAXSTORAGEQUOTA:
            System.out.println("Storage space allowed for Quota Root "
                + arguments[1]
                + " : "
                + formatStorageValue(probe.getMaxStorage(arguments[1])));
            break;
        case GETMAXMESSAGECOUNTQUOTA:
            System.out.println("Message count allowed for Quota Root " + arguments[1] + " : " + formatMessageValue(probe.getMaxMessageCount(arguments[1])));
            break;
        case SETMAXSTORAGEQUOTA:
            probe.setMaxStorage(arguments[1], ValueWithUnit.parse(arguments[2]).getConvertedValue());
            break;
        case SETMAXMESSAGECOUNTQUOTA:
            probe.setMaxMessageCount(arguments[1], Long.parseLong(arguments[2]));
            break;
        case SETDEFAULTMAXSTORAGEQUOTA:
            probe.setDefaultMaxStorage(ValueWithUnit.parse(arguments[1]).getConvertedValue());
            break;
        case SETDEFAULTMAXMESSAGECOUNTQUOTA:
            probe.setDefaultMaxMessageCount(Long.parseLong(arguments[1]));
            break;
        case GETDEFAULTMAXSTORAGEQUOTA:
            System.out.println("Default Maximum Storage Quota : " + formatStorageValue(probe.getDefaultMaxStorage()));
            break;
        case GETDEFAULTMAXMESSAGECOUNTQUOTA:
            System.out.println("Default Maximum message count Quota : " + formatMessageValue(probe.getDefaultMaxMessageCount()));
            break;
        default:
            throw new UnrecognizedCommandException(cmdType.getCommand());
        }
    }

    private static void print(String[] data, PrintStream out) {
        if (data != null) {
            for (String u : data) {
                out.println(u);
            }
            out.println(Joiner.on('\n').join(data));
        }
    }

    private void printStorageQuota(String quotaRootString, SerializableQuota quota) {
        System.out.println(String.format("Storage quota for %s is : %s / %s",
            quotaRootString,
            formatStorageValue(quota.getUsed()),
            formatStorageValue(quota.getMax())));
    }

    private void printMessageQuota(String quotaRootString, SerializableQuota quota) {
        System.out.println(String.format("Message count quota for %s is : %s / %s",
            quotaRootString,
            formatMessageValue(quota.getUsed()),
            formatMessageValue(quota.getMax())));
    }

    private String formatStorageValue(long value) {
        if (value == Quota.UNKNOWN) {
            return ValueWithUnit.UNKNOWN;
        }
        if (value == Quota.UNLIMITED) {
            return ValueWithUnit.UNLIMITED;
        }
        return FileUtils.byteCountToDisplaySize(value);
    }

    private String formatMessageValue(long value) {
        if (value == Quota.UNKNOWN) {
            return ValueWithUnit.UNKNOWN;
        }
        if (value == Quota.UNLIMITED) {
            return ValueWithUnit.UNLIMITED;
        }
        return String.valueOf(value);
    }

    private void print(Map<String, Collection<String>> map, PrintStream out) {
        if (map != null) {
            for (Entry<String, Collection<String>> entry : map.entrySet()) {
                out.println(entry.getKey() + '=' + collectionToString(entry));
            }
            out.println();
        }
    }

    private String collectionToString(Entry<String, Collection<String>> entry) {
        return Joiner.on(',').join(entry.getValue());
    }

    private static void printUsage() {
        StringBuilder footerBuilder = new StringBuilder();
        for (CmdType cmdType : CmdType.values()) {
            footerBuilder.append(cmdType.getUsage()).append("\n");
        }
        new HelpFormatter().printHelp(
            String.format("java %s --host <arg> <command>%n", ServerCmd.class.getName()),
            "",
            createOptions(),
            footerBuilder.toString());
    }

}
