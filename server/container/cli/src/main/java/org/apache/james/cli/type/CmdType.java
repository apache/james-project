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

/**
 * Enumeration of valid command types.
 */
public enum CmdType {
    ADDUSER("adduser", "username","password"),
    REMOVEUSER("removeuser", "username"),
    LISTUSERS("listusers"),
    ADDDOMAIN("adddomain", "domainname"),
    REMOVEDOMAIN("removedomain", "domainname"),
    CONTAINSDOMAIN("containsdomain", "domainname"),
    LISTDOMAINS("listdomains"),
    LISTMAPPINGS("listmappings"),
    LISTUSERDOMAINMAPPINGS("listuserdomainmappings", "user","domain"),
    ADDADDRESSMAPPING("addaddressmapping", "user","domain", "fromaddress"),
    REMOVEADDRESSMAPPING("removeaddressmapping", "user","domain", "fromaddress"),
    ADDREGEXMAPPING("addregexmapping", "user","domain", "regex"),
    REMOVEREGEXMAPPING("removeregexmapping", "user","domain", "regex"),
    SETPASSWORD("setpassword", "username","password"),
    COPYMAILBOX("copymailbox", "srcbean","dstbean"),
    DELETEUSERMAILBOXES("deleteusermailboxes", "user"),
    CREATEMAILBOX("createmailbox", "namespace", "user", "name"),
    LISTUSERMAILBOXES("listusermailboxes", "user"),
    DELETEMAILBOX("deletemailbox", "namespace", "user", "name"),
    GETSTORAGEQUOTA("getstoragequota", "quotaroot"),
    GETMESSAGECOUNTQUOTA("getmessagecountquota", "quotaroot"),
    GETQUOTAROOT("getquotaroot", "namespace", "user", "name"),
    GETMAXSTORAGEQUOTA("getmaxstoragequota", "quotaroot"),
    GETMAXMESSAGECOUNTQUOTA("getmaxmessagecountquota", "quotaroot"),
    SETMAXSTORAGEQUOTA("setmaxstoragequota", "quotaroot", "max_message_count"),
    SETMAXMESSAGECOUNTQUOTA("setmaxmessagecountquota", "quotaroot", "max_storage"),
    SETDEFAULTMAXSTORAGEQUOTA("setdefaultmaxstoragequota", "max_storage"),
    SETDEFAULTMAXMESSAGECOUNTQUOTA("setdefaultmaxmessagecountquota", "max_message_count"),
    GETDEFAULTMAXSTORAGEQUOTA("getdefaultmaxstoragequota"),
    GETDEFAULTMAXMESSAGECOUNTQUOTA("getdefaultmaxmessagecountquota");

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
            for (CmdType cmd : values()) {
                if (cmd.getCommand().equalsIgnoreCase(command)) {
                    return cmd;
                }
            }
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
        for(String argument : arguments) {
            stringBuilder.append(" <" + argument + ">");
        }
        return stringBuilder.toString();
    }
}
