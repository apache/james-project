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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.james.adapter.mailbox.SerializableQuota;
import org.apache.james.cli.exceptions.InvalidArgumentNumberException;
import org.apache.james.cli.exceptions.InvalidPortException;
import org.apache.james.cli.exceptions.MissingCommandException;
import org.apache.james.cli.exceptions.UnrecognizedCommandException;
import org.apache.james.cli.probe.ServerProbe;
import org.apache.james.cli.type.CmdType;
import org.apache.james.mailbox.model.Quota;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;

public class ServerCmdTest {

    public static final String ADDITIONAL_ARGUMENT = "additionalArgument";
    private IMocksControl control;

    private ServerProbe serverProbe;

    private ServerCmd testee;

    @Before
    public void setup() {
        control = createControl();
        serverProbe = control.createMock(ServerProbe.class);
        testee = new ServerCmd(serverProbe);
    }

    @Test
    public void addDomainCommandShouldWork() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDDOMAIN.getCommand(), domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.addDomain(domain);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void removeDomainCommandShouldWork() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEDOMAIN.getCommand(), domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.removeDomain(domain);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void containsDomainCommandShouldWork() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.CONTAINSDOMAIN.getCommand(), domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        expect(serverProbe.containsDomain(domain)).andReturn(true);

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void listDomainsCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTDOMAINS.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        String[] res = {};
        expect(serverProbe.listDomains()).andReturn(res);

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void addUserCommandShouldWork() throws Exception {
        String user = "user@domain";
        String password = "password";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDUSER.getCommand(), user, password};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.addUser(user, password);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void removeUserCommandShouldWork() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEUSER.getCommand(), user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.removeUser(user);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void listUsersCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERS.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        String[] res = {};
        expect(serverProbe.listUsers()).andReturn(res);

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void listMappingsCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTMAPPINGS.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        expect(serverProbe.listMappings()).andReturn(new HashMap<String, Collection<String>>());

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void listUserDomainMappingsCommandShouldWork() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERDOMAINMAPPINGS.getCommand(), user, domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        expect(serverProbe.listUserDomainMappings(user, domain)).andReturn(new ArrayList<String>());

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void addAddressCommandShouldWork() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String address = "bis@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDADDRESSMAPPING.getCommand(), user, domain, address};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.addAddressMapping(user, domain, address);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void removeAddressCommandShouldWork() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String address = "bis@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEADDRESSMAPPING.getCommand(), user, domain, address};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.removeAddressMapping(user, domain, address);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void addRegexMappingCommandShouldWork() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String regex = "bis.*@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDREGEXMAPPING.getCommand(), user, domain, regex};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.addRegexMapping(user, domain, regex);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void removeRegexMappingCommandShouldWork() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String regex = "bis.*@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEREGEXMAPPING.getCommand(), user, domain, regex};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.removeRegexMapping(user, domain, regex);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void setPasswordMappingCommandShouldWork() throws Exception {
        String user = "user@domain";
        String password = "pass";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETPASSWORD.getCommand(), user, password};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.setPassword(user, password);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void copyMailboxMappingCommandShouldWork() throws Exception {
        String srcBean = "srcBean";
        String dstBean = "dstBean";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.COPYMAILBOX.getCommand(), srcBean, dstBean};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.copyMailbox(srcBean, dstBean);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void deleteUserMailboxesMappingCommandShouldWork() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.DELETEUSERMAILBOXES.getCommand(), user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.deleteUserMailboxesNames(user);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void createMailboxMappingCommandShouldWork() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String name = "INBOX.test";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.CREATEMAILBOX.getCommand(), namespace, user, name};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.createMailbox(namespace, user, name);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void deleteMailboxMappingCommandShouldWork() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String name = "INBOX.test";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.DELETEMAILBOX.getCommand(), namespace, user, name};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.deleteMailbox(namespace, user, name);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void listUserMailboxesMappingsCommandShouldWork() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERMAILBOXES.getCommand(), user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        expect(serverProbe.listUserMailboxes(user)).andReturn(new ArrayList<String>());

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void getQuotaRootCommandShouldWork() throws Exception {
        String namespace = "#private";
        String user = "user@domain";
        String name = "INBOX";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETQUOTAROOT.getCommand(), namespace, user, name};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        expect(serverProbe.getQuotaRoot(namespace, user, name)).andReturn(namespace + "&" + user);

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void getDefaultMaxMessageCountCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETDEFAULTMAXMESSAGECOUNTQUOTA.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        expect(serverProbe.getDefaultMaxMessageCount()).andReturn(1024L * 1024L);

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void getDefaultMaxStorageCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETDEFAULTMAXSTORAGEQUOTA.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        expect(serverProbe.getDefaultMaxStorage()).andReturn(1024L * 1024L * 1024L);

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void setDefaultMaxMessageCountCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETDEFAULTMAXMESSAGECOUNTQUOTA.getCommand(), "1054"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

       serverProbe.setDefaultMaxMessageCount(1054);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void setDefaultMaxStorageCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETDEFAULTMAXSTORAGEQUOTA.getCommand(), "1G"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.setDefaultMaxStorage(1024 * 1024 * 1024);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void setMaxMessageCountCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETMAXMESSAGECOUNTQUOTA.getCommand(), quotaroot, "1000"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.setMaxMessageCount(quotaroot, 1000);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void setMaxStorageCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETMAXSTORAGEQUOTA.getCommand(), quotaroot, "5M"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        serverProbe.setMaxStorage(quotaroot, 5 * 1024 * 1024);
        expectLastCall();

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void getMaxMessageCountCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETMAXMESSAGECOUNTQUOTA.getCommand(), quotaroot};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        expect(serverProbe.getMaxMessageCount(quotaroot)).andReturn(Quota.UNLIMITED);

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void getMaxStorageQuotaCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETMAXSTORAGEQUOTA.getCommand(), quotaroot};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        expect(serverProbe.getMaxStorage(quotaroot)).andReturn(Quota.UNLIMITED);

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void getStorageQuotaCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETSTORAGEQUOTA.getCommand(), quotaroot};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        expect(serverProbe.getStorageQuota(quotaroot)).andReturn(new SerializableQuota(Quota.UNLIMITED, Quota.UNKNOWN));

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test
    public void getMessageCountQuotaCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETMESSAGECOUNTQUOTA.getCommand(), quotaroot};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        expect(serverProbe.getMessageCountQuota(quotaroot)).andReturn(new SerializableQuota(Quota.UNLIMITED, Quota.UNKNOWN));

        control.replay();
        testee.executeCommandLine(commandLine);
        control.verify();
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void addDomainCommandShouldThrowOnMissingArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDDOMAIN.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void removeDomainCommandShouldThrowOnMissingArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEDOMAIN.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void containsDomainCommandShouldThrowOnMissingArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.CONTAINSDOMAIN.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void addUserCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDUSER.getCommand(), user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void removeUserCommandShouldThrowOnMissingArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEUSER.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void listUserDomainMappingsCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERDOMAINMAPPINGS.getCommand(), user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void addAddressCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDADDRESSMAPPING.getCommand(), user, domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void removeAddressCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEADDRESSMAPPING.getCommand(), user, domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void addRegexMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDREGEXMAPPING.getCommand(), user, domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void removeRegexMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEREGEXMAPPING.getCommand(), user, domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void setPasswordMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETPASSWORD.getCommand(), user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void copyMailboxMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String srcBean = "srcBean";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.COPYMAILBOX.getCommand(), srcBean};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void deleteUserMailboxesMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.DELETEUSERMAILBOXES.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void createMailboxMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.CREATEMAILBOX.getCommand(), namespace, user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void deleteMailboxMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.DELETEMAILBOX.getCommand(), namespace, user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void listUserMailboxesMappingsCommandShouldThrowOnMissingArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERMAILBOXES.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void addDomainCommandShouldThrowOnAdditionalArguments() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDDOMAIN.getCommand(), domain, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void removeDomainCommandShouldThrowOnAdditionalArguments() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEDOMAIN.getCommand(), domain, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void containsDomainCommandShouldThrowOnAdditionalArguments() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.CONTAINSDOMAIN.getCommand(), domain, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void listDomainsCommandShouldThrowOnAdditionalArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTDOMAINS.getCommand(), ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void addUserCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String password = "password";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDUSER.getCommand(), user, password, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void removeUserCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEUSER.getCommand(), user, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void listUsersCommandShouldThrowOnAdditionalArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERS.getCommand(), ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void listMappingsCommandShouldThrowOnAdditionalArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTMAPPINGS.getCommand(), ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void listUserDomainMappingsCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERDOMAINMAPPINGS.getCommand(), user, domain, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void addAddressCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String address = "bis@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDADDRESSMAPPING.getCommand(), user, domain, address, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void removeAddressCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String address = "bis@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEADDRESSMAPPING.getCommand(), user, domain, address, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void addRegexMappingCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String regex = "bis.*@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDREGEXMAPPING.getCommand(), user, domain, regex, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void removeRegexMappingCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String regex = "bis.*@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEREGEXMAPPING.getCommand(), user, domain, regex, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void setPasswordMappingCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String password = "pass";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETPASSWORD.getCommand(), user, password, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void copyMailboxMappingCommandShouldThrowOnAdditionalArguments() throws Exception {
        String srcBean = "srcBean";
        String dstBean = "dstBean";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.COPYMAILBOX.getCommand(), srcBean, dstBean, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void deleteUserMailboxesMappingCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.DELETEUSERMAILBOXES.getCommand(), user, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void createMailboxMappingCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String name = "INBOX.test";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.CREATEMAILBOX.getCommand(), namespace, user, name, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void deleteMailboxMappingCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String name = "INBOX.test";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.DELETEMAILBOX.getCommand(), namespace, user, name, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = InvalidArgumentNumberException.class)
    public void listUserMailboxesMappingsCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERMAILBOXES.getCommand(), user, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = UnrecognizedCommandException.class)
    public void executeCommandLineShouldThrowOnUnrecognizedCommands() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "wrongCommand"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        control.replay();
        try {
            testee.executeCommandLine(commandLine);
        } finally {
            control.verify();
        }
    }

    @Test(expected = MissingCommandException.class)
    public void parseCommandLineShouldThrowWhenOnlyOptionAreProvided() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999" };
        ServerCmd.parseCommandLine(arguments);
    }

    @Test(expected = ParseException.class)
    public void parseCommandLineShouldThrowWhenInvalidOptionIsProvided() throws Exception {
        String[] arguments = { "-v", "-h", "127.0.0.1", "-p", "9999" };
        ServerCmd.parseCommandLine(arguments);
    }

    @Test(expected = ParseException.class)
    public void parseCommandLineShouldThrowWhenMandatoryOptionIsMissing() throws Exception {
        String[] arguments = { "-v", "-h", "127.0.0.1", "-p", "9999" };
        ServerCmd.parseCommandLine(arguments);
    }

    @Test
    public void parseCommandLineShouldReturnACommandLineWithCorrectArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);
        assertThat(commandLine.getArgs()).containsExactly("command", "arg1", "arg2", "arg3");
    }

    @Test
    public void parseCommandLineShouldReturnACommandLineWithCorrectPortOption() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);
        assertThat(commandLine.getOptionValue(ServerCmd.PORT_OPT_LONG)).isEqualTo("9999");
    }

    @Test
    public void parseCommandLineShouldReturnACommandLineWithCorrectHostOption() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);
        assertThat(commandLine.getOptionValue(ServerCmd.HOST_OPT_LONG)).isEqualTo("127.0.0.1");
    }

    @Test
    public void getPortShouldRetrievePort() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);
        assertThat(ServerCmd.getPort(commandLine)).isEqualTo(9999);
    }

    @Test(expected = InvalidPortException.class)
    public void getPortShouldThrowOnNullPortValueOption() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "0", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine;
        try {
            commandLine = ServerCmd.parseCommandLine(arguments);
        } catch (Exception e) {
            fail("Exception received", e);
            return;
        }
        ServerCmd.getPort(commandLine);
    }

    @Test(expected = InvalidPortException.class)
    public void getPortShouldThrowOnNegativePortValueOption() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "-1", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine;
        try {
            commandLine = ServerCmd.parseCommandLine(arguments);
        } catch (Exception e) {
            fail("Exception received", e);
            return;
        }
        ServerCmd.getPort(commandLine);
    }

    @Test(expected = InvalidPortException.class)
    public void getPortShouldThrowOnTooHighPortValueOption() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "99999", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine;
        try {
            commandLine = ServerCmd.parseCommandLine(arguments);
        } catch (Exception e) {
            fail("Exception received", e);
            return;
        }
        ServerCmd.getPort(commandLine);
    }

}
