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
package org.apache.james.transport.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.transport.mailets.redirect.AbstractRedirect;
import org.apache.james.transport.mailets.redirect.InitParameters;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;
import org.junit.Before;
import org.junit.Test;

public class SenderUtilsTest {

    private InitParameters initParameters;
    private MailAddress postmaster;
    private SenderUtils testee;

    @Before
    public void setup() throws Exception {
        AbstractRedirect mailet = mock(AbstractRedirect.class);
        initParameters = mock(InitParameters.class);
        when(mailet.getInitParameters())
            .thenReturn(initParameters);

        MailetContext mailetContext = mock(MailetContext.class);
        postmaster = new MailAddress("postmaster@james.org");
        when(mailetContext.getPostmaster())
            .thenReturn(postmaster);
        when(mailet.getMailetContext())
            .thenReturn(mailetContext);

        testee = SenderUtils.from(mailet);
    }

    @Test
    public void getSenderShouldReturnNullWhenSenderInitParameterIsNull() throws Exception {
        when(initParameters.getSender())
            .thenReturn(null);

        MailAddress sender = testee.getSender();

        assertThat(sender).isNull();
    }

    @Test
    public void getSenderShouldReturnNullWhenSenderInitParameterIsEmpty() throws Exception {
        when(initParameters.getSender())
            .thenReturn("");

        MailAddress sender = testee.getSender();

        assertThat(sender).isNull();
    }

    @Test
    public void getSenderShouldReturnFirstMailAddressWhenMultipleAddresses() throws Exception {
        when(initParameters.getSender())
            .thenReturn("test@james.org, test2@james.org");

        MailAddress sender = testee.getSender();

        MailAddress expectedMailAddress = new MailAddress("test", "james.org");
        assertThat(sender).isEqualTo(expectedMailAddress);
    }

    @Test
    public void getSenderShouldReturnPostmasterSpecialAddressWhenFirstAddressIsSpecialPostmaster() throws Exception {
        when(initParameters.getSender())
            .thenReturn("postmaster, test2@james.org");

        MailAddress sender = testee.getSender();

        assertThat(sender).isEqualTo(postmaster);
    }

    @Test
    public void getSenderShouldReturnSenderSpecialAddressWhenFirstAddressIsSpecialSender() throws Exception {
        when(initParameters.getSender())
            .thenReturn("sender, test2@james.org");

        MailAddress sender = testee.getSender();

        MailAddress expectedMailAddress = SpecialAddress.SENDER;
        assertThat(sender).isEqualTo(expectedMailAddress);
    }

    @Test
    public void getSenderShouldReturnUnalteredSpecialAddressWhenFirstAddressIsSpecialUnaltered() throws Exception {
        when(initParameters.getSender())
            .thenReturn("unaltered, test2@james.org");

        MailAddress sender = testee.getSender();

        MailAddress expectedMailAddress = SpecialAddress.UNALTERED;
        assertThat(sender).isEqualTo(expectedMailAddress);
    }
}
