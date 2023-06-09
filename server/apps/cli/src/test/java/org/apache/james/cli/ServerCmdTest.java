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

import com.google.common.collect.ImmutableList;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.apache.james.cli.exceptions.InvalidArgumentNumberException;
import org.apache.james.cli.exceptions.MissingCommandException;
import org.apache.james.cli.exceptions.UnrecognizedCommandException;
import org.apache.james.cli.probe.impl.*;
import org.apache.james.cli.type.CmdType;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.SerializableQuota;
import org.apache.james.mailbox.model.SerializableQuotaLimitValue;
import org.apache.james.rrt.lib.MappingsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ServerCmdTest {

    private static final String ADDITIONAL_ARGUMENT = "additionalArgument";

    private JmxDataProbe dataProbe;
    private JmxMailboxProbe mailboxProbe;
    private JmxQuotaProbe quotaProbe;
    private JmxSieveProbe sieveProbe;

    private ServerCmd testee;

    @BeforeEach
    void setup() {
        dataProbe = mock(JmxDataProbe.class);
        mailboxProbe = mock(JmxMailboxProbe.class);
        quotaProbe = mock(JmxQuotaProbe.class);
        sieveProbe = mock(JmxSieveProbe.class);
        testee = new ServerCmd(dataProbe, mailboxProbe, quotaProbe, sieveProbe);
    }

    @Test
    void addDomainCommandShouldWork() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDDOMAIN.getCommand(), domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(dataProbe).addDomain(domain);
    }

    @Test
    void removeDomainCommandShouldWork() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEDOMAIN.getCommand(), domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(dataProbe).removeDomain(domain);
    }

    @Test
    void containsDomainCommandShouldWork() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.CONTAINSDOMAIN.getCommand(), domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(dataProbe.containsDomain(domain)).thenReturn(true);

        testee.executeCommandLine(commandLine);
    }

    @Test
    void listDomainsCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTDOMAINS.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(dataProbe.listDomains()).thenReturn(ImmutableList.of());

        testee.executeCommandLine(commandLine);
    }

    @Test
    void addDomainMappingCommandShouldWork() throws Exception {
        String domain = "example.com";
        String targetDomain = "other.example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDDOMAINMAPPING.getCommand(), domain, targetDomain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(dataProbe).addDomainMapping(domain, targetDomain);
    }

    @Test
    void removeDomainMappingCommandShouldWork() throws Exception {
        String domain = "example.com";
        String targetDomain = "other.example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEDOMAINMAPPING.getCommand(), domain, targetDomain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(dataProbe).removeDomainMapping(domain, targetDomain);
    }

    @Test
    void listDomainMappingsCommandShouldWork() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTDOMAINMAPPINGS.getCommand(), domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(dataProbe.listDomainMappings(domain)).thenReturn(MappingsImpl.empty());

        testee.executeCommandLine(commandLine);
    }

    @Test
    void addUserCommandShouldWork() throws Exception {
        String user = "user@domain";
        String password = "password";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDUSER.getCommand(), user, password};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(dataProbe).addUser(user, password);
    }

    @Test
    void removeUserCommandShouldWork() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEUSER.getCommand(), user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(dataProbe).removeUser(user);
    }

    @Test
    void listUsersCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERS.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        String[] res = {};
        when(dataProbe.listUsers()).thenReturn(res);

        testee.executeCommandLine(commandLine);
    }

    @Test
    void listMappingsCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTMAPPINGS.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(dataProbe.listMappings()).thenReturn(new HashMap<>());

        testee.executeCommandLine(commandLine);
    }

    @Test
    void listUserDomainMappingsCommandShouldWork() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERDOMAINMAPPINGS.getCommand(), user, domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(dataProbe.listUserDomainMappings(user, domain)).thenReturn(MappingsImpl.empty());

        testee.executeCommandLine(commandLine);
    }

    @Test
    void addAddressCommandShouldWork() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String address = "bis@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDADDRESSMAPPING.getCommand(), user, domain, address};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(dataProbe).addAddressMapping(user, domain, address);
    }

    @Test
    void removeAddressCommandShouldWork() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String address = "bis@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEADDRESSMAPPING.getCommand(), user, domain, address};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(dataProbe).removeAddressMapping(user, domain, address);
    }

    @Test
    void addRegexMappingCommandShouldWork() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String regex = "bis.*@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDREGEXMAPPING.getCommand(), user, domain, regex};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(dataProbe).addRegexMapping(user, domain, regex);
    }

    @Test
    void removeRegexMappingCommandShouldWork() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String regex = "bis.*@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEREGEXMAPPING.getCommand(), user, domain, regex};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(dataProbe).removeRegexMapping(user, domain, regex);
    }

    @Test
    void setPasswordMappingCommandShouldWork() throws Exception {
        String user = "user@domain";
        String password = "pass";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETPASSWORD.getCommand(), user, password};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(dataProbe).setPassword(user, password);
    }

    @Test
    void copyMailboxMappingCommandShouldWork() throws Exception {
        String srcBean = "srcBean";
        String dstBean = "dstBean";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.COPYMAILBOX.getCommand(), srcBean, dstBean};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(mailboxProbe).copyMailbox(srcBean, dstBean);
    }

    @Test
    void deleteUserMailboxesMappingCommandShouldWork() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.DELETEUSERMAILBOXES.getCommand(), user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(mailboxProbe).deleteUserMailboxesNames(user);
    }

    @Test
    void createMailboxMappingCommandShouldWork() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String name = "INBOX.test";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.CREATEMAILBOX.getCommand(), namespace, user, name};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(mailboxProbe.createMailbox(namespace, user, name)).thenReturn(mock(MailboxId.class));

        testee.executeCommandLine(commandLine);
    }

    @Test
    void deleteMailboxMappingCommandShouldWork() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String name = "INBOX.test";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.DELETEMAILBOX.getCommand(), namespace, user, name};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(mailboxProbe).deleteMailbox(namespace, user, name);
    }
    
    @Test
    void importEmlFileToMailboxCommandShouldWork() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String name = "INBOX.test";
        String emlpath = "./src/test/resources/eml/frnog.eml";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.IMPORTEML.getCommand(), namespace, user, name, emlpath};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(mailboxProbe).importEmlFileToMailbox(namespace, user, name, emlpath);
    }

    @Test
    void listUserMailboxesMappingsCommandShouldWork() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERMAILBOXES.getCommand(), user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(mailboxProbe.listUserMailboxes(user)).thenReturn(new ArrayList<>());

        testee.executeCommandLine(commandLine);
    }

    @Test
    void getQuotaRootCommandShouldWork() throws Exception {
        String namespace = "#private";
        String user = "user@domain";
        String name = "INBOX";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETQUOTAROOT.getCommand(), namespace, user, name};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(quotaProbe.getQuotaRoot(namespace, user, name)).thenReturn(namespace + "&" + user);

        testee.executeCommandLine(commandLine);
    }

    @Test
    void getGlobalMaxMessageCountCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETGLOBALMAXMESSAGECOUNTQUOTA.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(quotaProbe.getGlobalMaxMessageCount()).thenReturn(new SerializableQuotaLimitValue<>(QuotaCountLimit.count(1024L * 1024L)));

        testee.executeCommandLine(commandLine);
    }

    @Test
    void getGlobalMaxStorageCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETGLOBALMAXSTORAGEQUOTA.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(quotaProbe.getGlobalMaxStorage()).thenReturn(new SerializableQuotaLimitValue<>(QuotaSizeLimit.size(1024L * 1024L * 1024L)));

        testee.executeCommandLine(commandLine);
    }

    @Test
    void setGlobalMaxMessageCountCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETGLOBALMAXMESSAGECOUNTQUOTA.getCommand(), "1054"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(quotaProbe).setGlobalMaxMessageCount(new SerializableQuotaLimitValue<>(QuotaCountLimit.count(1054)));
    }

    @Test
    void setGlobalMaxMessageCountCommandShouldWorkWhenUnlimited() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "--", CmdType.SETGLOBALMAXMESSAGECOUNTQUOTA.getCommand(), "-1"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(quotaProbe).setGlobalMaxMessageCount(new SerializableQuotaLimitValue<>(QuotaCountLimit.unlimited()));
    }

    @Test
    void setGlobalMaxMessageCountCommandShouldThrowWhenNegativeAndNotUnlimited() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "--", CmdType.SETGLOBALMAXMESSAGECOUNTQUOTA.getCommand(), "-2"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setGlobalMaxStorageCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETGLOBALMAXSTORAGEQUOTA.getCommand(), "1G"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(quotaProbe).setGlobalMaxStorage(new SerializableQuotaLimitValue<>(QuotaSizeLimit.size(1024 * 1024 * 1024)));
    }

    @Test
    void setGlobalMaxStorageCommandShouldWorkWhenUnlimited() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "--", CmdType.SETGLOBALMAXSTORAGEQUOTA.getCommand(), "-1"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(quotaProbe).setGlobalMaxStorage(new SerializableQuotaLimitValue<>(QuotaSizeLimit.unlimited()));
    }

    @Test
    void setGlobalMaxStorageCommandShouldThrowWhenNegativeAndNotUnlimited() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "--", CmdType.SETGLOBALMAXSTORAGEQUOTA.getCommand(), "-2"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setMaxMessageCountCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETMAXMESSAGECOUNTQUOTA.getCommand(), quotaroot, "1000"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(quotaProbe).setMaxMessageCount(quotaroot, new SerializableQuotaLimitValue<>(QuotaCountLimit.count(1000)));
    }

    @Test
    void setMaxMessageCountCommandShouldWorkWhenUnlimited() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "--", CmdType.SETMAXMESSAGECOUNTQUOTA.getCommand(), quotaroot, "-1"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(quotaProbe).setMaxMessageCount(quotaroot, new SerializableQuotaLimitValue<>(QuotaCountLimit.unlimited()));
    }

    @Test
    void setMaxMessageCountCommandShouldThrowWhenNegativeAndNotUnlimited() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "--", CmdType.SETMAXMESSAGECOUNTQUOTA.getCommand(), quotaroot, "-2"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setMaxStorageCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETMAXSTORAGEQUOTA.getCommand(), quotaroot, "5M"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(quotaProbe).setMaxStorage(quotaroot, new SerializableQuotaLimitValue<>(QuotaSizeLimit.size(5 * 1024 * 1024)));
    }

    @Test
    void setMaxStorageCommandShouldWorkWhenUnlimited() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "--", CmdType.SETMAXSTORAGEQUOTA.getCommand(), quotaroot, "-1"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(quotaProbe).setMaxStorage(quotaroot, new SerializableQuotaLimitValue<>(QuotaSizeLimit.unlimited()));
    }

    @Test
    void setMaxStorageCommandShouldThrowWhenNegativeAndNotUnlimited() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "--", CmdType.SETMAXSTORAGEQUOTA.getCommand(), quotaroot, "-2"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getMaxMessageCountCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETMAXMESSAGECOUNTQUOTA.getCommand(), quotaroot};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(quotaProbe.getMaxMessageCount(quotaroot)).thenReturn(new SerializableQuotaLimitValue<>(QuotaCountLimit.unlimited()));

        testee.executeCommandLine(commandLine);
    }

    @Test
    void getMaxStorageQuotaCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETMAXSTORAGEQUOTA.getCommand(), quotaroot};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(quotaProbe.getMaxStorage(quotaroot)).thenReturn(new SerializableQuotaLimitValue<>(QuotaSizeLimit.unlimited()));

        testee.executeCommandLine(commandLine);
    }

    @Test
    void getStorageQuotaCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETSTORAGEQUOTA.getCommand(), quotaroot};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(quotaProbe.getStorageQuota(quotaroot)).thenReturn(SerializableQuota.newInstance(QuotaSizeUsage.size(12), QuotaSizeLimit.unlimited()));

        testee.executeCommandLine(commandLine);
    }

    @Test
    void getMessageCountQuotaCommandShouldWork() throws Exception {
        String quotaroot = "#private&user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETMESSAGECOUNTQUOTA.getCommand(), quotaroot};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(quotaProbe.getMessageCountQuota(quotaroot)).thenReturn(SerializableQuota.newInstance(QuotaCountUsage.count(12), QuotaCountLimit.unlimited()));

        testee.executeCommandLine(commandLine);
    }

    @Test
    void reIndexAllQuotaCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REINDEXALL.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(mailboxProbe).reIndexAll();
    }

    @Test
    void reIndexMailboxCommandShouldWork() throws Exception {
        String namespace = "#private";
        String user = "btellier@apache.org";
        String name = "INBOX";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REINDEXMAILBOX.getCommand(), namespace, user, name};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(mailboxProbe).reIndexMailbox(namespace, user, name);
    }

    @Test
    void setSieveQuotaCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETSIEVEQUOTA.getCommand(), "2K"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(sieveProbe).setSieveQuota(2048);
    }

    @Test
    void setSieveUserQuotaCommandShouldWork() throws Exception {
        String user = "btellier@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETSIEVEUSERQUOTA.getCommand(), user, "1K"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(sieveProbe).setSieveQuota(user, 1024);
    }

    @Test
    void getSieveQuotaCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETSIEVEQUOTA.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(sieveProbe.getSieveQuota()).thenReturn(18L);

        testee.executeCommandLine(commandLine);

        verify(sieveProbe).getSieveQuota();
    }

    @Test
    void getSieveUserQuotaCommandShouldWork() throws Exception {
        String user = "btellier@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETSIEVEUSERQUOTA.getCommand(), user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        when(sieveProbe.getSieveQuota(user)).thenReturn(18L);

        testee.executeCommandLine(commandLine);

        verify(sieveProbe).getSieveQuota(user);
    }

    @Test
    void removeSieveQuotaCommandShouldWork() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVESIEVEQUOTA.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(sieveProbe).removeSieveQuota();
    }

    @Test
    void removeSieveUserQuotaCommandShouldWork() throws Exception {
        String user = "btellier@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVESIEVEUSERQUOTA.getCommand(), user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        testee.executeCommandLine(commandLine);

        verify(sieveProbe).removeSieveQuota(user);
    }

    @Test
    void addDomainCommandShouldThrowOnMissingArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDDOMAIN.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void removeDomainCommandShouldThrowOnMissingArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEDOMAIN.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);
        
        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void containsDomainCommandShouldThrowOnMissingArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.CONTAINSDOMAIN.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void addDomainMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDDOMAINMAPPING.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
                .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void removeDomainMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEDOMAINMAPPING.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
                .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void listDomainMappingsCommandShouldThrowOnMissingArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTDOMAINMAPPINGS.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
                .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void addUserCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDUSER.getCommand(), user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void removeUserCommandShouldThrowOnMissingArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEUSER.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void listUserDomainMappingsCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERDOMAINMAPPINGS.getCommand(), user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void addAddressCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDADDRESSMAPPING.getCommand(), user, domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void removeAddressCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEADDRESSMAPPING.getCommand(), user, domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void addRegexMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDREGEXMAPPING.getCommand(), user, domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void removeRegexMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEREGEXMAPPING.getCommand(), user, domain};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void setPasswordMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETPASSWORD.getCommand(), user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void copyMailboxMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String srcBean = "srcBean";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.COPYMAILBOX.getCommand(), srcBean};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void deleteUserMailboxesMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.DELETEUSERMAILBOXES.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void createMailboxMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.CREATEMAILBOX.getCommand(), namespace, user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void deleteMailboxMappingCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.DELETEMAILBOX.getCommand(), namespace, user};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }


    @Test
    void importEmlFileToMailboxCommandShouldThrowOnMissingArguments() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String name = "INBOX.test";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.IMPORTEML.getCommand(), namespace, user, name};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void listUserMailboxesMappingsCommandShouldThrowOnMissingArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERMAILBOXES.getCommand()};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void addDomainCommandShouldThrowOnAdditionalArguments() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDDOMAIN.getCommand(), domain, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void removeDomainCommandShouldThrowOnAdditionalArguments() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEDOMAIN.getCommand(), domain, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void containsDomainCommandShouldThrowOnAdditionalArguments() throws Exception {
        String domain = "example.com";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.CONTAINSDOMAIN.getCommand(), domain, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void listDomainsCommandShouldThrowOnAdditionalArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTDOMAINS.getCommand(), ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void addUserCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String password = "password";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDUSER.getCommand(), user, password, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void removeUserCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEUSER.getCommand(), user, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void listUsersCommandShouldThrowOnAdditionalArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERS.getCommand(), ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void listMappingsCommandShouldThrowOnAdditionalArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTMAPPINGS.getCommand(), ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void listUserDomainMappingsCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERDOMAINMAPPINGS.getCommand(), user, domain, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void addAddressCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String address = "bis@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDADDRESSMAPPING.getCommand(), user, domain, address, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void removeAddressCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String address = "bis@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEADDRESSMAPPING.getCommand(), user, domain, address, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void addRegexMappingCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String regex = "bis.*@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDREGEXMAPPING.getCommand(), user, domain, regex, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void removeRegexMappingCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String domain = "domain";
        String regex = "bis.*@apache.org";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVEREGEXMAPPING.getCommand(), user, domain, regex, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void setPasswordMappingCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String password = "pass";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETPASSWORD.getCommand(), user, password, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void copyMailboxMappingCommandShouldThrowOnAdditionalArguments() throws Exception {
        String srcBean = "srcBean";
        String dstBean = "dstBean";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.COPYMAILBOX.getCommand(), srcBean, dstBean, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void deleteUserMailboxesMappingCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.DELETEUSERMAILBOXES.getCommand(), user, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void createMailboxMappingCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String name = "INBOX.test";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.CREATEMAILBOX.getCommand(), namespace, user, name, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void deleteMailboxMappingCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String name = "INBOX.test";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.DELETEMAILBOX.getCommand(), namespace, user, name, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void importEmlFileToMailboxCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String name = "INBOX.test";
        String emlpath = "./src/test/resources/eml/frnog.eml";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.IMPORTEML.getCommand(), namespace, user, name, emlpath, ADDITIONAL_ARGUMENT};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void listUserMailboxesMappingsCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.LISTUSERMAILBOXES.getCommand(), user, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void reIndexAllCommandShouldThrowOnAdditionalArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REINDEXALL.getCommand(), ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void reIndexMailboxCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String namespace = "#private";
        String name = "INBOX.test";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REINDEXMAILBOX.getCommand(), namespace, user, name, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void removeSieveQuotaCommandShouldThrowOnAdditionalArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVESIEVEQUOTA.getCommand(), ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void removeSieveUserQuotaCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.REMOVESIEVEUSERQUOTA.getCommand(), user, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void getSieveQuotaCommandShouldThrowOnAdditionalArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETSIEVEQUOTA.getCommand(), ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void setSieveQuotaCommandShouldThrowOnAdditionalArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETSIEVEQUOTA.getCommand(), "64K", ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void getSieveUserQuotaCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.GETSIEVEUSERQUOTA.getCommand(), user, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void setSieveUserQuotaCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.SETSIEVEUSERQUOTA.getCommand(), user, "64K", ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void addActiveSieveScriptCommandShouldThrowOnAdditionalArguments() throws Exception {
        String user = "user@domain";
        String scriptName = "sieve_script";
        String scriptPath = "./src/test/resources/sieve/sieve_script";

        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", CmdType.ADDACTIVESIEVESCRIPT.getCommand(), user, scriptName, scriptPath, ADDITIONAL_ARGUMENT };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
                .isInstanceOf(InvalidArgumentNumberException.class);
    }

    @Test
    void executeCommandLineShouldThrowOnUnrecognizedCommands() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "wrongCommand"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> testee.executeCommandLine(commandLine))
            .isInstanceOf(UnrecognizedCommandException.class);
    }

    @Test
    void parseCommandLineShouldThrowWhenOnlyOptionAreProvided() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999" };

        assertThatThrownBy(() -> ServerCmd.parseCommandLine(arguments))
            .isInstanceOf(MissingCommandException.class);
    }

    @Test
    void parseCommandLineShouldThrowWhenInvalidOptionIsProvided() throws Exception {
        String[] arguments = { "-v", "-h", "127.0.0.1", "-p", "9999" };

        assertThatThrownBy(() -> ServerCmd.parseCommandLine(arguments))
            .isInstanceOf(ParseException.class);
    }

    @Test
    void parseCommandLineShouldThrowWhenMandatoryOptionIsMissing() throws Exception {
        String[] arguments = { "-v", "-h", "127.0.0.1", "-p", "9999" };

        assertThatThrownBy(() -> ServerCmd.parseCommandLine(arguments))
            .isInstanceOf(ParseException.class);
    }

    @Test
    void parseCommandLineShouldReturnACommandLineWithCorrectArguments() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);
        assertThat(commandLine.getArgs()).containsExactly("command", "arg1", "arg2", "arg3");
    }

    @Test
    void parseCommandLineShouldReturnACommandLineWithCorrectPortOption() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);
        assertThat(commandLine.getOptionValue(ServerCmd.PORT_OPT_LONG)).isEqualTo("9999");
    }

    @Test
    void parseCommandLineShouldReturnACommandLineWithCorrectHostOption() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);
        assertThat(commandLine.getOptionValue(ServerCmd.HOST_OPT_LONG)).isEqualTo("127.0.0.1");
    }

    @Test
    void getHostShouldUseDefaultValueWhenNone() throws Exception {
        String[] arguments = { "-p", "9999", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);
        assertThat(ServerCmd.getHost(commandLine)).isEqualTo("127.0.0.1");
    }

    @Test
    void getHostShouldUseDefaultValueWhenEmpty() throws Exception {
        String[] arguments = { "-h", "", "-p", "9999", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);
        assertThat(ServerCmd.getHost(commandLine)).isEqualTo("127.0.0.1");
    }

    @Test
    void getHostShouldReturnValueWhenGiven() throws Exception {
        String[] arguments = { "-h", "123.4.5.6", "-p", "9999", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);
        assertThat(ServerCmd.getHost(commandLine)).isEqualTo("123.4.5.6");
    }

    @Test
    void getPortShouldUseDefaultValueWhenNone() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);
        assertThat(ServerCmd.getPort(commandLine)).isEqualTo(9999);
    }

    @Test
    void getPortShouldUseDefaultValueWhenEmpty() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);
        assertThat(ServerCmd.getPort(commandLine)).isEqualTo(9999);
    }

    @Test
    void getPortShouldRetrievePort() throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", "9999", "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);
        assertThat(ServerCmd.getPort(commandLine)).isEqualTo(9999);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "99999"})
    void getPortShouldThrowOnInvalidPortValueOption(String arg) throws Exception {
        String[] arguments = { "-h", "127.0.0.1", "-p", arg, "command", "arg1", "arg2", "arg3" };
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThatThrownBy(() -> ServerCmd.getPort(commandLine))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getAuthCredentialShouldReturnEmptyWhenNotGiven(@TempDir Path tempDir) throws Exception {
        String[] arguments = {"-h", "127.0.0.1", "-p", "99999", "command", "arg1", "arg2", "arg3"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThat(ServerCmd.getAuthCredential(commandLine, tempDir.toString()))
            .isEmpty();
    }

    @Test
    void getAuthCredentialShouldReturnValueWhenGivenViaCommandLine(@TempDir Path tempDir) throws Exception {
        String[] arguments = {"-h", "127.0.0.1", "-p", "99999", "-username", "james-admin", "-password", "123456", "command", "arg1", "arg2", "arg3"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        assertThat(ServerCmd.getAuthCredential(commandLine, tempDir.toString()))
            .isEqualTo(Optional.of(new JmxConnection.AuthCredential("james-admin", "123456")));
    }

    @Test
    void getAuthCredentialShouldReturnValueWhenGivenViaJmxPasswordFile(@TempDir Path tempDir) throws Exception {
        String[] arguments = {"-h", "127.0.0.1", "-p", "99999", "command", "arg1", "arg2", "arg3"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        File passwordFile = new File(tempDir.toString() + "/jmxremote.password");
        try (OutputStream outputStream = new FileOutputStream(passwordFile)) {
            IOUtils.write("james-admin1 pass2\n", outputStream, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }

        assertThat(ServerCmd.getAuthCredential(commandLine, passwordFile.getPath()))
            .isEqualTo(Optional.of(new JmxConnection.AuthCredential("james-admin1", "pass2")));
    }

    @Test
    void getAuthCredentialShouldPreferCommandlineValue(@TempDir Path tempDir) throws Exception {
        String[] arguments = {"-h", "127.0.0.1", "-p", "99999", "-username", "james-admin", "-password", "123456", "command", "arg1", "arg2", "arg3"};
        CommandLine commandLine = ServerCmd.parseCommandLine(arguments);

        File passwordFile = new File(tempDir.toString() + "/jmxremote.password");
        try (OutputStream outputStream = new FileOutputStream(passwordFile)) {
            IOUtils.write("james-admin1 pass2\n", outputStream, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }

        assertThat(ServerCmd.getAuthCredential(commandLine, tempDir.toString()))
            .isEqualTo(Optional.of(new JmxConnection.AuthCredential("james-admin", "123456")));
    }

}
