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
package org.apache.james.cli.type;

import java.util.Arrays;

/**
 * Enumeration of valid command types.
 */
public enum CmdType {
    ADDUSER("AddUser", "username","password"),
    REMOVEUSER("RemoveUser", "username"),
    LISTUSERS("ListUsers"),
    ADDDOMAIN("AddDomain", "domainName"),
    REMOVEDOMAIN("RemoveDomain", "domainName"),
    CONTAINSDOMAIN("ContainsDomain", "domainName"),
    LISTDOMAINS("ListDomains"),
    ADDDOMAINMAPPING("AddDomainMapping", "domain", "targetDomain"),
    REMOVEDOMAINMAPPING("RemoveDomainMapping", "domain", "targetDomain"),
    LISTDOMAINMAPPINGS("ListDomainMappings", "domain"),
    LISTMAPPINGS("ListMappings"),
    LISTUSERDOMAINMAPPINGS("ListUserDomainMappings", "user","domain"),
    ADDADDRESSMAPPING("AddAddressMapping", "fromUser","fromDomain", "toAddress"),
    REMOVEADDRESSMAPPING("RemoveAddressMapping", "fromUser","fromDomain", "toAddress"),
    ADDREGEXMAPPING("AddRegexMapping", "user","domain", "regex"),
    REMOVEREGEXMAPPING("RemoveRegexMapping", "user","domain", "regex"),
    SETPASSWORD("SetPassword", "username","password"),
    COPYMAILBOX("CopyMailbox", "srcBean","dstBean"),
    DELETEUSERMAILBOXES("DeleteUserMailboxes", "user"),
    CREATEMAILBOX("CreateMailbox", "namespace", "user", "name"),
    LISTUSERMAILBOXES("ListUserMailboxes", "user"),
    DELETEMAILBOX("DeleteMailbox", "namespace", "user", "name"),
    IMPORTEML("ImportEml", "namespace", "user", "name", "path"),
    GETSTORAGEQUOTA("GetStorageQuota", "quotaroot"),
    GETMESSAGECOUNTQUOTA("GetMessageCountQuota", "quotaroot"),
    GETQUOTAROOT("GetQuotaroot", "namespace", "user", "name"),
    GETMAXSTORAGEQUOTA("GetMaxStorageQuota", "quotaroot"),
    GETMAXMESSAGECOUNTQUOTA("GetMaxMessageCountQuota", "quotaroot"),
    SETMAXSTORAGEQUOTA("SetMaxStorageQuota", "quotaroot", "maxMessageCount"),
    SETMAXMESSAGECOUNTQUOTA("SetMaxMessageCountQuota", "quotaroot", "maxStorage"),
    SETGLOBALMAXSTORAGEQUOTA("SetGlobalMaxStorageQuota", "maxStorage"),
    SETGLOBALMAXMESSAGECOUNTQUOTA("SetGlobalMaxMessageCountQuota", "maxMessageCount"),
    GETGLOBALMAXSTORAGEQUOTA("GetGlobalMaxStorageQuota"),
    GETGLOBALMAXMESSAGECOUNTQUOTA("GetGlobalMaxMessageCountQuota"),
    REINDEXMAILBOX("ReindexMailbox", "namespace", "user", "name"),
    REINDEXALL("ReindexAll"),
    GETSIEVEQUOTA("GetSieveQuota"),
    SETSIEVEQUOTA("SetSieveQuota", "quota"),
    REMOVESIEVEQUOTA("RemoveSieveQuota"),
    GETSIEVEUSERQUOTA("GetSieveUserQuota", "username"),
    SETSIEVEUSERQUOTA("SetSieveUserQuota", "username", "quota"),
    REMOVESIEVEUSERQUOTA("RemoveSieveUserQuota", "username"),
    ADDACTIVESIEVESCRIPT("AddActiveSieveScript", "username", "scriptname", "path");

    private final String command;
    private final String[] arguments;

    CmdType(String command, String... arguments) {
        this.command = command;
        this.arguments = arguments;
    }

    /**
     * Validate that the number of arguments match the passed value.
     *
     * @param arguments
     *            The number of argument to compare.
     * @return true if values match, false otherwise.
     */
    public boolean hasCorrectArguments(int arguments) {
        return this.arguments.length + 1 == arguments;

    }

    /**
     * Return a CmdType enumeration that matches the passed command.
     *
     * @param command
     *            The command to use for lookup.
     * @return the CmdType enumeration that matches the passed command, or null
     *         if not found.
     */
    public static CmdType lookup(String command) {
        if (command != null) {
            return Arrays.stream(values())
                .filter(cmd -> cmd.getCommand().equalsIgnoreCase(command))
                .findFirst()
                .orElse(null);
        }
        return null;
    }

    /**
     * Return the value of command.
     *
     * @return the value of command.
     */
    public String getCommand() {
        return this.command;
    }

    /**
     * Return the value of arguments.
     *
     * @return the value of arguments.
     */
    public int getArgumentCount() {
        return this.arguments.length + 1;
    }

    public String getUsage() {
        StringBuilder stringBuilder = new StringBuilder(command);
        for (String argument : arguments) {
            stringBuilder.append(" <" + argument + ">");
        }
        return stringBuilder.toString();
    }
}
