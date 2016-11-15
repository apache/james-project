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
package org.apache.james.transport.mailets.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.mailet.Mail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MailMessageAlteringUtilsTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void buildShouldThrowWhenMailetIsNull() {
        expectedException.expect(NullPointerException.class);
        expectedException.expectMessage("'mailet' is mandatory");
        MailMessageAlteringUtils.from(null).build();
    }

    @Test
    public void buildShouldThrowWhenOriginalMailIsNull() {
        expectedException.expect(NullPointerException.class);
        expectedException.expectMessage("'originalMail' is mandatory");
        MailMessageAlteringUtils.from(mock(AbstractRedirect.class))
            .build();
    }

    @Test
    public void buildShouldThrowWhenNewMailIsNull() {
        expectedException.expect(NullPointerException.class);
        expectedException.expectMessage("'newMail' is mandatory");
        MailMessageAlteringUtils.from(mock(AbstractRedirect.class))
            .originalMail(mock(Mail.class))
            .build();
    }

    @Test
    public void buildShouldWorkWhenEverythingProvided() {
        MailMessageAlteringUtils.from(mock(AbstractRedirect.class))
            .originalMail(mock(Mail.class))
            .newMail(mock(Mail.class))
            .build();
    }

    @Test
    public void getFileNameShouldReturnNoSubjectWhenSubjectIsNull() {
        MailMessageAlteringUtils alteredMailUtils = MailMessageAlteringUtils.from(mock(AbstractRedirect.class))
                .originalMail(mock(Mail.class))
                .newMail(mock(Mail.class))
                .build();

        String fileName = alteredMailUtils.getFileName(null);

        assertThat(fileName).isEqualTo("No Subject");
    }

    @Test
    public void getFileNameShouldReturnNoSubjectWhenSubjectContainsOnlySpaces() {
        MailMessageAlteringUtils alteredMailUtils = MailMessageAlteringUtils.from(mock(AbstractRedirect.class))
                .originalMail(mock(Mail.class))
                .newMail(mock(Mail.class))
                .build();

        String fileName = alteredMailUtils.getFileName("    ");

        assertThat(fileName).isEqualTo("No Subject");
    }

    @Test
    public void getFileNameShouldReturnSubjectWhenSubjectIsGiven() {
        MailMessageAlteringUtils alteredMailUtils = MailMessageAlteringUtils.from(mock(AbstractRedirect.class))
                .originalMail(mock(Mail.class))
                .newMail(mock(Mail.class))
                .build();

        String fileName = alteredMailUtils.getFileName("my Subject");

        assertThat(fileName).isEqualTo("my Subject");
    }

    @Test
    public void getFileNameShouldReturnTrimmedSubjectWhenSubjectStartsWithSpaces() {
        MailMessageAlteringUtils alteredMailUtils = MailMessageAlteringUtils.from(mock(AbstractRedirect.class))
                .originalMail(mock(Mail.class))
                .newMail(mock(Mail.class))
                .build();

        String fileName = alteredMailUtils.getFileName("    my Subject");

        assertThat(fileName).isEqualTo("my Subject");
    }
}
