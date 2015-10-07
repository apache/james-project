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
package org.apache.james.mailbox.spring;

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.james.mailbox.copier.MailboxCopierImpl;
import org.junit.BeforeClass;
import org.junit.Test;

public class SpringMailboxTest {
    
    private static SpringMailbox springMailbox;
    
    @BeforeClass
    public static void beforeClass() {
        springMailbox = new SpringMailbox();
    }
    
    @Test
    public void testGetBean() throws IOException {
        assertEquals(AnonymousAuthenticator.class.getName(), springMailbox.getBean("authenticator").getClass().getName());
        assertEquals(MailboxCopierImpl.class.getName(), springMailbox.getBean("mailboxcopier").getClass().getName());
    }

}
