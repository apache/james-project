/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.mailbox.model;

import static org.junit.Assert.assertEquals;

import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLEntryKey;
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.MailboxACL.SpecialName;
import org.apache.james.mailbox.model.SimpleMailboxACL.SimpleMailboxACLEntryKey;
import org.junit.Test;

/**
 * @author Peter Palaga
 */
public class SimpleMailboxACLEntryKeyTest {
    
    private static final String GROUP_1 = "group1";

    private static final String USER_1 = "user1";
    
    @Test
    public void testUser() throws UnsupportedRightException {
        
        MailboxACLEntryKey k = new SimpleMailboxACLEntryKey(USER_1);
        assertEquals(k.isNegative(), false);
        assertEquals(k.getNameType(), NameType.user);
        assertEquals(k.getName(), USER_1);
        
    }
    
    @Test
    public void testNegativeUser() throws UnsupportedRightException {
        
        MailboxACLEntryKey k = new SimpleMailboxACLEntryKey(MailboxACL.DEFAULT_NEGATIVE_MARKER + USER_1);
        assertEquals(k.isNegative(), true);
        assertEquals(k.getNameType(), NameType.user);
        assertEquals(k.getName(), USER_1);
        
    }
    

    @Test
    public void testGroup() throws UnsupportedRightException {
        
        MailboxACLEntryKey k = new SimpleMailboxACLEntryKey(MailboxACL.DEFAULT_GROUP_MARKER + GROUP_1);
        assertEquals(k.isNegative(), false);
        assertEquals(k.getNameType(), NameType.group);
        assertEquals(k.getName(), GROUP_1);
        
    }
    
    @Test
    public void testNegativeGroup() throws UnsupportedRightException {
        
        MailboxACLEntryKey k = new SimpleMailboxACLEntryKey("" + MailboxACL.DEFAULT_NEGATIVE_MARKER + MailboxACL.DEFAULT_GROUP_MARKER + GROUP_1);
        assertEquals(k.isNegative(), true);
        assertEquals(k.getNameType(), NameType.group);
        assertEquals(k.getName(), GROUP_1);
        
    }
    

    @Test
    public void testOwner() throws UnsupportedRightException {
        
        MailboxACLEntryKey k = new SimpleMailboxACLEntryKey(SpecialName.owner.toString());
        assertEquals(k.isNegative(), false);
        assertEquals(k.getNameType(), NameType.special);
        assertEquals(k.getName(), SpecialName.owner.toString());
        
    }
    
    @Test
    public void testNegativeOwner() throws UnsupportedRightException {
        
        MailboxACLEntryKey k = new SimpleMailboxACLEntryKey(MailboxACL.DEFAULT_NEGATIVE_MARKER + SpecialName.owner.toString());
        assertEquals(k.isNegative(), true);
        assertEquals(k.getNameType(), NameType.special);
        assertEquals(k.getName(), SpecialName.owner.toString());
        
    }

    @Test
    public void testAnybody() throws UnsupportedRightException {
        
        MailboxACLEntryKey k = new SimpleMailboxACLEntryKey(SpecialName.anybody.toString());
        assertEquals(k.isNegative(), false);
        assertEquals(k.getNameType(), NameType.special);
        assertEquals(k.getName(), SpecialName.anybody.toString());
        
    }
    
    @Test
    public void testNegativeAnybody() throws UnsupportedRightException {
        
        MailboxACLEntryKey k = new SimpleMailboxACLEntryKey(MailboxACL.DEFAULT_NEGATIVE_MARKER + SpecialName.anybody.toString());
        assertEquals(k.isNegative(), true);
        assertEquals(k.getNameType(), NameType.special);
        assertEquals(k.getName(), SpecialName.anybody.toString());
        
    }
    

    @Test
    public void testAuthenticated() throws UnsupportedRightException {
        
        MailboxACLEntryKey k = new SimpleMailboxACLEntryKey(SpecialName.authenticated.toString());
        assertEquals(k.isNegative(), false);
        assertEquals(k.getNameType(), NameType.special);
        assertEquals(k.getName(), SpecialName.authenticated.toString());
        
    }
    
    @Test
    public void testNegativeAuthenticated() throws UnsupportedRightException {
        
        MailboxACLEntryKey k = new SimpleMailboxACLEntryKey(MailboxACL.DEFAULT_NEGATIVE_MARKER + SpecialName.authenticated.toString());
        assertEquals(k.isNegative(), true);
        assertEquals(k.getNameType(), NameType.special);
        assertEquals(k.getName(), SpecialName.authenticated.toString());
        
    }
}
