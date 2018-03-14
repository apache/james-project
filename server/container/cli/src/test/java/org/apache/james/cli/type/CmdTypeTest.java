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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Test class for the CmdType enum.
 */
public class CmdTypeTest {

    @Test
    public void hasCorrectArgumentShouldBeFalseOnNegativeInput() {
        assertThat(CmdType.ADDDOMAIN.hasCorrectArguments(-1)).isFalse();
    }
    
    @Test
    public void hasCorrectArgumentShouldBeTrueOnRightArgumentNumber() {
        assertThat(CmdType.ADDDOMAIN.hasCorrectArguments(2)).isTrue();
    }
    

    @Test
    public void hasCorrectArgumentShouldBeFalseOnIncorrectArgumentNumber() {
        assertThat(CmdType.ADDDOMAIN.hasCorrectArguments(1)).isFalse();
    }

    @Test 
    public void lookupAddUserShouldReturnEnumValue() {
        assertThat(CmdType.lookup("adduser")).isEqualTo(CmdType.ADDUSER);
    }
    
    @Test 
    public void lookupRemoveUserShouldReturnEnumValue() {
        assertThat(CmdType.lookup("removeuser")).isEqualTo(CmdType.REMOVEUSER);
    }
    
    @Test 
    public void lookupListUsersShouldReturnEnumValue() {
        assertThat(CmdType.lookup("listusers")).isEqualTo(CmdType.LISTUSERS);
    }
    
    @Test 
    public void lookupAddDomainShouldReturnEnumValue() {
        assertThat(CmdType.lookup("adddomain")).isEqualTo(CmdType.ADDDOMAIN);
    }
    
    @Test 
    public void lookupRemoveDomainShouldReturnEnumValue() {
        assertThat(CmdType.lookup("removedomain")).isEqualTo(CmdType.REMOVEDOMAIN);
    }
    
    @Test 
    public void lookupContainsDomainShouldReturnEnumValue() {
        assertThat(CmdType.lookup("containsdomain")).isEqualTo(CmdType.CONTAINSDOMAIN);
    }
    
    @Test 
    public void lookupListDomainsShouldReturnEnumValue() {
        assertThat(CmdType.lookup("listdomains")).isEqualTo(CmdType.LISTDOMAINS);
    }
    
    @Test 
    public void lookupListMappingsShouldReturnEnumValue() {
        assertThat(CmdType.lookup("listmappings")).isEqualTo(CmdType.LISTMAPPINGS);
    }
    
    @Test 
    public void lookupListUserDomainMappingsShouldReturnEnumValue() {
        assertThat(CmdType.lookup("listuserdomainmappings")).isEqualTo(CmdType.LISTUSERDOMAINMAPPINGS);
    }
    
    @Test 
    public void lookupAddAddressMappingShouldReturnEnumValue() {
        assertThat(CmdType.lookup("addaddressmapping")).isEqualTo(CmdType.ADDADDRESSMAPPING);
    }
    
    @Test 
    public void lookupRemoveAddressMappingShouldReturnEnumValue() {
        assertThat(CmdType.lookup("removeaddressmapping")).isEqualTo(CmdType.REMOVEADDRESSMAPPING);
    }
    
    @Test 
    public void lookupAddRegexMappingShouldReturnEnumValue() {
        assertThat(CmdType.lookup("addregexmapping")).isEqualTo(CmdType.ADDREGEXMAPPING);
    }
    
    @Test 
    public void lookupRemoveRegexMappingShouldReturnEnumValue() {
        assertThat(CmdType.lookup("removeregexmapping")).isEqualTo(CmdType.REMOVEREGEXMAPPING);
    }
    
    @Test 
    public void lookupSetPasswordShouldReturnEnumValue() {
        assertThat(CmdType.lookup("setpassword")).isEqualTo(CmdType.SETPASSWORD);
    }
    
    @Test 
    public void lookupCopyMailboxShouldReturnEnumValue() {
        assertThat(CmdType.lookup("copymailbox")).isEqualTo(CmdType.COPYMAILBOX);
    }
    
    @Test 
    public void lookupDeleteUserMailboxesShouldReturnEnumValue() {
        assertThat(CmdType.lookup("deleteusermailboxes")).isEqualTo(CmdType.DELETEUSERMAILBOXES);
    }

    @Test 
    public void lookupCreateMailboxShouldReturnEnumValue() {
        assertThat(CmdType.lookup("createmailbox")).isEqualTo(CmdType.CREATEMAILBOX);
    }

    @Test 
    public void lookupListUserMailboxesShouldReturnEnumValue() {
        assertThat(CmdType.lookup("listusermailboxes")).isEqualTo(CmdType.LISTUSERMAILBOXES);
    }

    @Test 
    public void lookupDeleteMailboxShouldReturnEnumValue() {
        assertThat(CmdType.lookup("deletemailbox")).isEqualTo(CmdType.DELETEMAILBOX);
    }

    @Test
    public void lookupImportEmlFileToMailboxShouldReturnEnumValue() {
        assertThat(CmdType.lookup("ImportEml"))
            .isEqualTo(CmdType.IMPORTEML);
    }

    @Test
    public void lookupSetGlobalMaxStorageQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("setglobalmaxstoragequota")).isEqualTo(CmdType.SETGLOBALMAXSTORAGEQUOTA);
    }

    @Test
    public void lookupSetGlobalMaxMessageCountQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("setglobalmaxmessagecountquota")).isEqualTo(CmdType.SETGLOBALMAXMESSAGECOUNTQUOTA);
    }

    @Test
    public void lookupGetGlobalMaxStorageQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getglobalmaxstoragequota")).isEqualTo(CmdType.GETGLOBALMAXSTORAGEQUOTA);
    }

    @Test
    public void lookupGetGlobalMaxMessageCountQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getglobalmaxmessagecountquota")).isEqualTo(CmdType.GETGLOBALMAXMESSAGECOUNTQUOTA);
    }

    @Test
    public void lookupSetMaxStorageQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("setmaxstoragequota")).isEqualTo(CmdType.SETMAXSTORAGEQUOTA);
    }

    @Test
    public void lookupSetMaxMessageCountQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("setmaxmessagecountquota")).isEqualTo(CmdType.SETMAXMESSAGECOUNTQUOTA);
    }

    @Test
    public void lookupGetMaxStorageQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getmaxstoragequota")).isEqualTo(CmdType.GETMAXSTORAGEQUOTA);
    }

    @Test
    public void lookupGetMaxMessageCountQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getmaxmessagecountquota")).isEqualTo(CmdType.GETMAXMESSAGECOUNTQUOTA);
    }

    @Test
    public void lookupGetStorageQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getstoragequota")).isEqualTo(CmdType.GETSTORAGEQUOTA);
    }

    @Test
    public void lookupGetMessageCountQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getmessagecountquota")).isEqualTo(CmdType.GETMESSAGECOUNTQUOTA);
    }

    @Test
    public void lookupReIndexMailboxShouldReturnEnumValue() {
        assertThat(CmdType.lookup("reindexall")).isEqualTo(CmdType.REINDEXALL);
    }

    @Test
    public void lookupReIndexAllShouldReturnEnumValue() {
        assertThat(CmdType.lookup("reindexmailbox")).isEqualTo(CmdType.REINDEXMAILBOX);
    }

    @Test
    public void lookupGetSieveQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getsievequota")).isEqualTo(CmdType.GETSIEVEQUOTA);
    }

    @Test
    public void lookupGetSieveUserQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getsieveuserquota")).isEqualTo(CmdType.GETSIEVEUSERQUOTA);
    }

    @Test
    public void lookupSetSieveQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("setsievequota")).isEqualTo(CmdType.SETSIEVEQUOTA);
    }

    @Test
    public void lookupSetSieveUserQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("setsieveuserquota")).isEqualTo(CmdType.SETSIEVEUSERQUOTA);
    }

    @Test
    public void lookupRemoveSieveQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("removesievequota")).isEqualTo(CmdType.REMOVESIEVEQUOTA);
    }

    @Test
    public void lookupRemoveSieveUserQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("removesieveuserquota")).isEqualTo(CmdType.REMOVESIEVEUSERQUOTA);
    }

    @Test 
    public void lookupEmptyStringShouldReturnNull() {
        assertThat(CmdType.lookup("")).isNull();
    }

    @Test 
    public void lookupUnknownStringShouldReturnNull() {
        assertThat(CmdType.lookup("error")).isNull();
    }

    @Test 
    public void lookupNullShouldReturnNull() {
        assertThat(CmdType.lookup(null)).isNull();
    }
    
    @Test
    public void usageShouldOutputCommandNamesAndArguments() {
        assertThat(CmdType.CREATEMAILBOX.getUsage()).isEqualTo("CreateMailbox <namespace> <user> <name>");
    }
    
}