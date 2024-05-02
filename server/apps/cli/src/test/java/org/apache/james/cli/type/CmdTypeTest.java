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

import org.junit.jupiter.api.Test;

/**
 * Test class for the CmdType enum.
 */
class CmdTypeTest {

    @Test
    void hasCorrectArgumentShouldBeFalseOnNegativeInput() {
        assertThat(CmdType.ADDDOMAIN.hasCorrectArguments(-1)).isFalse();
    }
    
    @Test
    void hasCorrectArgumentShouldBeTrueOnRightArgumentNumber() {
        assertThat(CmdType.ADDDOMAIN.hasCorrectArguments(2)).isTrue();
    }
    

    @Test
    void hasCorrectArgumentShouldBeFalseOnIncorrectArgumentNumber() {
        assertThat(CmdType.ADDDOMAIN.hasCorrectArguments(1)).isFalse();
    }

    @Test 
    void lookupAddUserShouldReturnEnumValue() {
        assertThat(CmdType.lookup("adduser")).isEqualTo(CmdType.ADDUSER);
    }
    
    @Test 
    void lookupRemoveUserShouldReturnEnumValue() {
        assertThat(CmdType.lookup("removeuser")).isEqualTo(CmdType.REMOVEUSER);
    }
    
    @Test 
    void lookupListUsersShouldReturnEnumValue() {
        assertThat(CmdType.lookup("listusers")).isEqualTo(CmdType.LISTUSERS);
    }
    
    @Test 
    void lookupAddDomainShouldReturnEnumValue() {
        assertThat(CmdType.lookup("adddomain")).isEqualTo(CmdType.ADDDOMAIN);
    }
    
    @Test 
    void lookupRemoveDomainShouldReturnEnumValue() {
        assertThat(CmdType.lookup("removedomain")).isEqualTo(CmdType.REMOVEDOMAIN);
    }
    
    @Test 
    void lookupContainsDomainShouldReturnEnumValue() {
        assertThat(CmdType.lookup("containsdomain")).isEqualTo(CmdType.CONTAINSDOMAIN);
    }
    
    @Test 
    void lookupListDomainsShouldReturnEnumValue() {
        assertThat(CmdType.lookup("listdomains")).isEqualTo(CmdType.LISTDOMAINS);
    }

    @Test
    void lookupAddDomainMappingShouldReturnEnumValue() {
        assertThat(CmdType.lookup("adddomainmapping")).isEqualTo(CmdType.ADDDOMAINMAPPING);
    }

    @Test
    void lookupRemoveDomainMappingShouldReturnEnumValue() {
        assertThat(CmdType.lookup("removedomainmapping")).isEqualTo(CmdType.REMOVEDOMAINMAPPING);
    }

    @Test
    void lookupListDomainMappingShouldReturnEnumValue() {
        assertThat(CmdType.lookup("listdomainmappings")).isEqualTo(CmdType.LISTDOMAINMAPPINGS);
    }
    
    @Test 
    void lookupListMappingsShouldReturnEnumValue() {
        assertThat(CmdType.lookup("listmappings")).isEqualTo(CmdType.LISTMAPPINGS);
    }
    
    @Test 
    void lookupListUserDomainMappingsShouldReturnEnumValue() {
        assertThat(CmdType.lookup("listuserdomainmappings")).isEqualTo(CmdType.LISTUSERDOMAINMAPPINGS);
    }
    
    @Test 
    void lookupAddAddressMappingShouldReturnEnumValue() {
        assertThat(CmdType.lookup("addaddressmapping")).isEqualTo(CmdType.ADDADDRESSMAPPING);
    }
    
    @Test 
    void lookupRemoveAddressMappingShouldReturnEnumValue() {
        assertThat(CmdType.lookup("removeaddressmapping")).isEqualTo(CmdType.REMOVEADDRESSMAPPING);
    }
    
    @Test 
    void lookupAddRegexMappingShouldReturnEnumValue() {
        assertThat(CmdType.lookup("addregexmapping")).isEqualTo(CmdType.ADDREGEXMAPPING);
    }
    
    @Test 
    void lookupRemoveRegexMappingShouldReturnEnumValue() {
        assertThat(CmdType.lookup("removeregexmapping")).isEqualTo(CmdType.REMOVEREGEXMAPPING);
    }
    
    @Test 
    void lookupSetPasswordShouldReturnEnumValue() {
        assertThat(CmdType.lookup("setpassword")).isEqualTo(CmdType.SETPASSWORD);
    }
    
    @Test 
    void lookupCopyMailboxShouldReturnEnumValue() {
        assertThat(CmdType.lookup("copymailbox")).isEqualTo(CmdType.COPYMAILBOX);
    }
    
    @Test 
    void lookupDeleteUserMailboxesShouldReturnEnumValue() {
        assertThat(CmdType.lookup("deleteusermailboxes")).isEqualTo(CmdType.DELETEUSERMAILBOXES);
    }

    @Test 
    void lookupCreateMailboxShouldReturnEnumValue() {
        assertThat(CmdType.lookup("createmailbox")).isEqualTo(CmdType.CREATEMAILBOX);
    }

    @Test 
    void lookupListUserMailboxesShouldReturnEnumValue() {
        assertThat(CmdType.lookup("listusermailboxes")).isEqualTo(CmdType.LISTUSERMAILBOXES);
    }

    @Test 
    void lookupDeleteMailboxShouldReturnEnumValue() {
        assertThat(CmdType.lookup("deletemailbox")).isEqualTo(CmdType.DELETEMAILBOX);
    }

    @Test
    void lookupImportEmlFileToMailboxShouldReturnEnumValue() {
        assertThat(CmdType.lookup("ImportEml"))
            .isEqualTo(CmdType.IMPORTEML);
    }

    @Test
    void lookupSetGlobalMaxStorageQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("setglobalmaxstoragequota")).isEqualTo(CmdType.SETGLOBALMAXSTORAGEQUOTA);
    }

    @Test
    void lookupSetGlobalMaxMessageCountQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("setglobalmaxmessagecountquota")).isEqualTo(CmdType.SETGLOBALMAXMESSAGECOUNTQUOTA);
    }

    @Test
    void lookupGetGlobalMaxStorageQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getglobalmaxstoragequota")).isEqualTo(CmdType.GETGLOBALMAXSTORAGEQUOTA);
    }

    @Test
    void lookupGetGlobalMaxMessageCountQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getglobalmaxmessagecountquota")).isEqualTo(CmdType.GETGLOBALMAXMESSAGECOUNTQUOTA);
    }

    @Test
    void lookupSetMaxStorageQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("setmaxstoragequota")).isEqualTo(CmdType.SETMAXSTORAGEQUOTA);
    }

    @Test
    void lookupSetMaxMessageCountQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("setmaxmessagecountquota")).isEqualTo(CmdType.SETMAXMESSAGECOUNTQUOTA);
    }

    @Test
    void lookupGetMaxStorageQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getmaxstoragequota")).isEqualTo(CmdType.GETMAXSTORAGEQUOTA);
    }

    @Test
    void lookupGetMaxMessageCountQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getmaxmessagecountquota")).isEqualTo(CmdType.GETMAXMESSAGECOUNTQUOTA);
    }

    @Test
    void lookupGetStorageQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getstoragequota")).isEqualTo(CmdType.GETSTORAGEQUOTA);
    }

    @Test
    void lookupGetMessageCountQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getmessagecountquota")).isEqualTo(CmdType.GETMESSAGECOUNTQUOTA);
    }

    @Test
    void lookupReIndexMailboxShouldReturnEnumValue() {
        assertThat(CmdType.lookup("reindexall")).isEqualTo(CmdType.REINDEXALL);
    }

    @Test
    void lookupReIndexAllShouldReturnEnumValue() {
        assertThat(CmdType.lookup("reindexmailbox")).isEqualTo(CmdType.REINDEXMAILBOX);
    }

    @Test
    void lookupGetSieveQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getsievequota")).isEqualTo(CmdType.GETSIEVEQUOTA);
    }

    @Test
    void lookupGetSieveUserQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("getsieveuserquota")).isEqualTo(CmdType.GETSIEVEUSERQUOTA);
    }

    @Test
    void lookupSetSieveQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("setsievequota")).isEqualTo(CmdType.SETSIEVEQUOTA);
    }

    @Test
    void lookupSetSieveUserQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("setsieveuserquota")).isEqualTo(CmdType.SETSIEVEUSERQUOTA);
    }

    @Test
    void lookupRemoveSieveQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("removesievequota")).isEqualTo(CmdType.REMOVESIEVEQUOTA);
    }

    @Test
    void lookupRemoveSieveUserQuotaShouldReturnEnumValue() {
        assertThat(CmdType.lookup("removesieveuserquota")).isEqualTo(CmdType.REMOVESIEVEUSERQUOTA);
    }

    @Test
    void addSieveScriptShouldReturnEnumValue() {
        assertThat(CmdType.lookup("addactivesievescript")).isEqualTo(CmdType.ADDACTIVESIEVESCRIPT);
    }

    @Test 
    void lookupEmptyStringShouldReturnNull() {
        assertThat(CmdType.lookup("")).isNull();
    }

    @Test 
    void lookupUnknownStringShouldReturnNull() {
        assertThat(CmdType.lookup("error")).isNull();
    }

    @Test 
    void lookupNullShouldReturnNull() {
        assertThat(CmdType.lookup(null)).isNull();
    }
    
    @Test
    void usageShouldOutputCommandNamesAndArguments() {
        assertThat(CmdType.CREATEMAILBOX.getUsage()).isEqualTo("CreateMailbox <namespace> <user> <name>");
    }
    
}