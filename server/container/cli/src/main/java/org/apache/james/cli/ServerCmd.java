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

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.time.StopWatch;
import org.apache.james.cli.exceptions.InvalidArgumentNumberException;
import org.apache.james.cli.exceptions.JamesCliException;
import org.apache.james.cli.exceptions.MissingCommandException;
import org.apache.james.cli.exceptions.UnrecognizedCommandException;
import org.apache.james.cli.probe.impl.JmxConnection;
import org.apache.james.cli.probe.impl.JmxDataProbe;
import org.apache.james.cli.probe.impl.JmxMailboxProbe;
import org.apache.james.cli.probe.impl.JmxQuotaProbe;
import org.apache.james.cli.probe.impl.JmxSieveProbe;
import org.apache.james.cli.type.CmdType;
import org.apache.james.cli.utils.ValueWithUnit;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.store.mail.model.SerializableQuota;
import org.apache.james.mailbox.store.probe.MailboxProbe;
import org.apache.james.mailbox.store.probe.QuotaProbe;
import org.apache.james.mailbox.store.probe.SieveProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.util.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;

/**
 * Command line utility for managing various aspect of the James server.
 */
public class ServerCmd {
    public static final String HOST_OPT_LONG = "host";
    public static final String HOST_OPT_SHORT = "h";
    public static final String PORT_OPT_LONG = "port";
    public static final String PORT_OPT_SHORT = "p";

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 9999;
    private static final Logger LOG = LoggerFactory.getLogger(ServerCmd.class);
    
    private static Options createOptions() {
        return new Options()
                .addOption(HOST_OPT_SHORT, HOST_OPT_LONG, true, "node hostname or ip address")
                .addOption(PORT_OPT_SHORT, PORT_OPT_LONG, true, "remote jmx agent port number");
    }

    /**
     * Main method to initialize the class.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        try {
            doMain(args);
            System.exit(0);
        } catch (JamesCliException e) {
            failWithMessage(e.getMessage());
        } catch (ParseException e) {
            failWithMessage("Error parsing command line : " + e.getMessage());
        } catch (IOException ioe) {
            failWithMessage("Error connecting to remote JMX agent : " + ioe.getMessage());
        } catch (Exception e) {
            LOG.error("Error while playing command", e);
            failWithMessage("Error " + e.getClass() + " while executing command:" + e.getMessage());
        }
    }

    public static void doMain(String[] args) throws Exception {
        PrintStream printStream = System.out;
        executeAndOutputToStream(args, printStream);
    }

    public static void executeAndOutputToStream(String[] args, PrintStream printStream) throws Exception {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        CommandLine cmd = parseCommandLine(args);
        JmxConnection jmxConnection = new JmxConnection(getHost(cmd), getPort(cmd));
        CmdType cmdType = new ServerCmd(
                new JmxDataProbe().connect(jmxConnection),
                new JmxMailboxProbe().connect(jmxConnection),
                new JmxQuotaProbe().connect(jmxConnection),
                new JmxSieveProbe().connect(jmxConnection)
            )
            .executeCommandLine(cmd, printStream);
        stopWatch.split();
        print(new String[] { Joiner.on(' ')
                .join(cmdType.getCommand(), "command executed sucessfully in", stopWatch.getSplitTime(), "ms.")},
            printStream);
        stopWatch.stop();
    }

    private final DataProbe probe;
    private final MailboxProbe mailboxProbe;
    private final QuotaProbe quotaProbe;
    private final SieveProbe sieveProbe;
    
    public ServerCmd(DataProbe probe, MailboxProbe mailboxProbe, QuotaProbe quotaProbe, SieveProbe sieveProbe) {
        this.probe = probe;
        this.mailboxProbe = mailboxProbe;
        this.quotaProbe = quotaProbe;
        this.sieveProbe = sieveProbe;
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
    static String getHost(CommandLine cmd) {
        String host = cmd.getOptionValue(HOST_OPT_LONG);
        if (Strings.isNullOrEmpty(host)) {
            return DEFAULT_HOST;
        }
        return host;
    }

    @VisibleForTesting
    static int getPort(CommandLine cmd) throws ParseException {
        String portNum = cmd.getOptionValue(PORT_OPT_LONG);
        if (!Strings.isNullOrEmpty(portNum)) {
            try {
                int portNumber = Integer.parseInt(portNum);
                Port.assertValid(portNumber);
                return portNumber;
            } catch (NumberFormatException e) {
                throw new ParseException("Port must be a number");
            }
        }
        return DEFAULT_PORT;
    }

    private static void failWithMessage(String s) {
        System.err.println(s);
        printUsage();
        System.exit(1);
    }

    @VisibleForTesting
    private CmdType executeCommandLine(CommandLine commandLine, PrintStream printStream) throws Exception {
        String[] arguments = commandLine.getArgs();
        String cmdName = arguments[0];
        CmdType cmdType = CmdType.lookup(cmdName);
        if (cmdType == null) {
            throw  new UnrecognizedCommandException(cmdName);
        }
        if (! cmdType.hasCorrectArguments(arguments.length)) {
            throw new InvalidArgumentNumberException(cmdType, arguments.length);
        }
        executeCommand(arguments, cmdType, printStream);
        return cmdType;
    }


    @VisibleForTesting
    CmdType executeCommandLine(CommandLine commandLine) throws Exception {
        return executeCommandLine(commandLine, new PrintStream(System.out));
    }

    private void executeCommand(String[] arguments, CmdType cmdType, PrintStream printStream) throws Exception {
        switch (cmdType) {
        case ADDUSER:
            probe.addUser(arguments[1], arguments[2]);
            break;
        case REMOVEUSER:
            probe.removeUser(arguments[1]);
            break;
        case LISTUSERS:
            print(probe.listUsers(), printStream);
            break;
        case ADDDOMAIN:
            probe.addDomain(arguments[1]);
            break;
        case REMOVEDOMAIN:
            probe.removeDomain(arguments[1]);
            break;
        case CONTAINSDOMAIN:
            if (probe.containsDomain(arguments[1])) {
                printStream.println(arguments[1] + " exists");
            } else {
                printStream.println(arguments[1] + " does not exists");
            }
            break;
        case LISTDOMAINS:
            print(probe.listDomains(), printStream);
            break;
        case LISTMAPPINGS:
            print(probe.listMappings(), printStream);
            break;
        case LISTUSERDOMAINMAPPINGS:
            Mappings userDomainMappings = probe.listUserDomainMappings(arguments[1], arguments[2]);
            print(userDomainMappings.asStrings(), printStream);
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
            mailboxProbe.copyMailbox(arguments[1], arguments[2]);
            break;
        case DELETEUSERMAILBOXES:
            mailboxProbe.deleteUserMailboxesNames(arguments[1]);
            break;
        case CREATEMAILBOX:
            mailboxProbe.createMailbox(arguments[1], arguments[2], arguments[3]);
            break;
        case LISTUSERMAILBOXES:
            Collection<String> mailboxes = mailboxProbe.listUserMailboxes(arguments[1]);
            print(mailboxes.toArray(new String[0]), printStream);
            break;
        case DELETEMAILBOX:
            mailboxProbe.deleteMailbox(arguments[1], arguments[2], arguments[3]);
            break;
        case IMPORTEML:
            mailboxProbe.importEmlFileToMailbox(arguments[1], arguments[2], arguments[3], arguments[4]);
            break;
        case GETSTORAGEQUOTA:
            printStorageQuota(arguments[1], quotaProbe.getStorageQuota(arguments[1]), printStream);
            break;
        case GETMESSAGECOUNTQUOTA:
            printMessageQuota(arguments[1], quotaProbe.getMessageCountQuota(arguments[1]), printStream);
            break;
        case GETQUOTAROOT:
            printStream.println("Quota Root: " + quotaProbe.getQuotaRoot(arguments[1], arguments[2], arguments[3]));
            break;
        case GETMAXSTORAGEQUOTA:
            printStream.println("Storage space allowed for Quota Root "
                + arguments[1]
                + ": "
                + formatStorageValue(quotaProbe.getMaxStorage(arguments[1])));
            break;
        case GETMAXMESSAGECOUNTQUOTA:
            printStream.println("MailboxMessage count allowed for Quota Root " + arguments[1] + ": " + formatMessageValue(quotaProbe.getMaxMessageCount(arguments[1])));
            break;
        case SETMAXSTORAGEQUOTA:
            quotaProbe.setMaxStorage(arguments[1], ValueWithUnit.parse(arguments[2]).getConvertedValue());
            break;
        case SETMAXMESSAGECOUNTQUOTA:
            quotaProbe.setMaxMessageCount(arguments[1], Long.parseLong(arguments[2]));
            break;
        case SETDEFAULTMAXSTORAGEQUOTA:
            quotaProbe.setDefaultMaxStorage(ValueWithUnit.parse(arguments[1]).getConvertedValue());
            break;
        case SETDEFAULTMAXMESSAGECOUNTQUOTA:
            quotaProbe.setDefaultMaxMessageCount(Long.parseLong(arguments[1]));
            break;
        case GETDEFAULTMAXSTORAGEQUOTA:
            printStream.println("Default Maximum Storage Quota: " + formatStorageValue(quotaProbe.getDefaultMaxStorage()));
            break;
        case GETDEFAULTMAXMESSAGECOUNTQUOTA:
            printStream.println("Default Maximum message count Quota: " + formatMessageValue(quotaProbe.getDefaultMaxMessageCount()));
            break;
        case REINDEXMAILBOX:
            mailboxProbe.reIndexMailbox(arguments[1], arguments[2], arguments[3]);
            break;
        case REINDEXALL:
            mailboxProbe.reIndexAll();
            break;
        case SETSIEVEQUOTA:
            sieveProbe.setSieveQuota(ValueWithUnit.parse(arguments[1]).getConvertedValue());
            break;
        case SETSIEVEUSERQUOTA:
            sieveProbe.setSieveQuota(arguments[1], ValueWithUnit.parse(arguments[2]).getConvertedValue());
            break;
        case GETSIEVEQUOTA:
            printStream.println("Storage space allowed for Sieve scripts by default: "
                + formatStorageValue(sieveProbe.getSieveQuota()));
            break;
        case GETSIEVEUSERQUOTA:
            printStream.println("Storage space allowed for "
                + arguments[1]
                + " Sieve scripts: "
                + formatStorageValue(sieveProbe.getSieveQuota(arguments[1])));
            break;
        case REMOVESIEVEQUOTA:
            sieveProbe.removeSieveQuota();
            break;
        case REMOVESIEVEUSERQUOTA:
            sieveProbe.removeSieveQuota(arguments[1]);
            break;
        default:
            throw new UnrecognizedCommandException(cmdType.getCommand());
        }
    }

    private static void print(String[] data, PrintStream out) {
        print(Arrays.asList(data), out);
    }
    
    private static void print(Iterable<String> data, PrintStream out) {
        if (data != null) {
            out.println(Joiner.on('\n').join(data));
        }
    }

    private void printStorageQuota(String quotaRootString, SerializableQuota quota, PrintStream printStream) {
        printStream.println(String.format("Storage quota for %s is: %s / %s",
            quotaRootString,
            formatStorageValue(quota.getUsed()),
            formatStorageValue(quota.getMax())));
    }

    private void printMessageQuota(String quotaRootString, SerializableQuota quota, PrintStream printStream) {
        printStream.println(String.format("MailboxMessage count quota for %s is: %s / %s",
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

    private void print(Map<String, Mappings> map, PrintStream out) {
        if (map != null) {
            for (Entry<String, Mappings> entry : map.entrySet()) {
                out.println(entry.getKey() + '=' + entry.getValue().serialize());
            }
            out.println();
        }
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
