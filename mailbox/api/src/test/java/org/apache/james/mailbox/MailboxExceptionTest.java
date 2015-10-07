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
package org.apache.james.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.exception.MailboxException;
import org.junit.Test;
/**
 * Ensure that {@link MailboxException} construction is correct.
 */
public class MailboxExceptionTest {
    
    private static final String EXCEPTION_MESSAGE = "this is an exception message";
    private static final String CAUSE_MESSAGE = "this is a cause";
    private static final Exception EXCEPTION_CAUSE = new Exception(CAUSE_MESSAGE);
    
    @Test
    public void testMailboxExceptionMessage() {
        MailboxException mbe = new MailboxException(EXCEPTION_MESSAGE);
        assertThat(mbe).hasMessage(EXCEPTION_MESSAGE);
    }

    @Test
    public void testMailboxExceptionCause() {
        MailboxException mbe = new MailboxException(EXCEPTION_MESSAGE, EXCEPTION_CAUSE);
        assertThat(mbe).hasMessage(EXCEPTION_MESSAGE).hasCauseExactlyInstanceOf(Exception.class);
        assertThat(mbe.getCause()).hasMessage(CAUSE_MESSAGE);
    }

}
