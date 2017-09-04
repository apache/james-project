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
package org.apache.james.server.core;

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * testing common behavior of Mail implementors. subclasses automatically get
 * their Mail-behavior tested.
 */
public abstract class MailTestAllImplementations {

    /** provide the concrete implementation to test */
    protected abstract Mail createMailImplementation();

    protected void helperTestInitialState(Mail mail) {
        assertFalse("no initial attributes", mail.hasAttributes());
        assertNull("no initial error", mail.getErrorMessage());
        assertNotNull("initial last update set", mail.getLastUpdated());
        try {
            assertTrue("no initial recipient", mail.getRecipients().isEmpty());
        } catch (NullPointerException e) {
            // current behavior. *BUT*, shouldn't this method better return with
            // an empty list?!
        }
        assertEquals("initial remote address is localhost ip", "127.0.0.1", mail.getRemoteAddr());
        assertEquals("initial remote host is localhost", "localhost", mail.getRemoteHost());
        assertEquals("default initial state", Mail.DEFAULT, mail.getState());
    }

    protected void helperTestMessageSize(Mail mail, int expectedMsgSize) throws MessagingException {
        try {
            assertEquals("initial message size == " + expectedMsgSize, expectedMsgSize, mail.getMessageSize());
        } catch (NullPointerException e) {
            // current behavior. *BUT*, shouldn't this method return more
            // gracefully?!
        }
    }

    @Test
    public void testAttributes() {
        Mail mail = createMailImplementation();
        assertFalse("no initial attributes", mail.hasAttributes());
        assertFalse("attributes initially empty", mail.getAttributeNames().hasNext());
        assertNull("not found on emtpy list", mail.getAttribute("test"));
        assertNull("no previous item with key", mail.setAttribute("testKey", "testValue"));
        assertEquals("item found", "testValue", mail.getAttribute("testKey"));
        assertTrue("has attribute", mail.hasAttributes());
        assertEquals("item removed", "testValue", mail.removeAttribute("testKey"));
        assertNull("item no longer found", mail.getAttribute("testKey"));
    }
}
