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

public class ReversePathUtilsTest {

    private InitParameters initParameters;
    private MailAddress postmaster;
    private ReversePathUtils testee;

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

        testee = ReversePathUtils.from(mailet);
    }

    @Test
    public void getReversePathShouldReturnNullWhenReversePathInitParameterIsNull() throws Exception {
        when(initParameters.getReversePath())
            .thenReturn(null);

        MailAddress reversePath = testee.getReversePath();

        assertThat(reversePath).isNull();
    }

    @Test
    public void getReversePathShouldReturnNullWhenReversePathInitParameterIsEmpty() throws Exception {
        when(initParameters.getReversePath())
            .thenReturn("");

        MailAddress reversePath = testee.getReversePath();

        assertThat(reversePath).isNull();
    }

    @Test
    public void getReversePathShouldReturnFirstMailAddressWhenMultipleAddresses() throws Exception {
        when(initParameters.getReversePath())
            .thenReturn("test@james.org, test2@james.org");

        MailAddress reversePath = testee.getReversePath();

        MailAddress expectedMailAddress = new MailAddress("test", "james.org");
        assertThat(reversePath).isEqualTo(expectedMailAddress);
    }

    @Test
    public void getReversePathShouldReturnPostmasterSpecialAddressWhenFirstAddressIsSpecialPostmaster() throws Exception {
        when(initParameters.getReversePath())
            .thenReturn("postmaster, test2@james.org");

        MailAddress reversePath = testee.getReversePath();

        assertThat(reversePath).isEqualTo(postmaster);
    }

    @Test
    public void getReversePathShouldReturnSenderSpecialAddressWhenFirstAddressIsSpecialSender() throws Exception {
        when(initParameters.getReversePath())
            .thenReturn("sender, test2@james.org");

        MailAddress reversePath = testee.getReversePath();

        MailAddress expectedMailAddress = SpecialAddress.SENDER;
        assertThat(reversePath).isEqualTo(expectedMailAddress);
    }

    @Test
    public void getReversePathShouldReturnNullSpecialAddressWhenFirstAddressIsSpecialNull() throws Exception {
        when(initParameters.getReversePath())
            .thenReturn("null, test2@james.org");

        MailAddress reversePath = testee.getReversePath();

        MailAddress expectedMailAddress = SpecialAddress.NULL;
        assertThat(reversePath).isEqualTo(expectedMailAddress);
    }

    @Test
    public void getReversePathShouldReturnUnalteredSpecialAddressWhenFirstAddressIsSpecialUnaltered() throws Exception {
        when(initParameters.getReversePath())
            .thenReturn("unaltered, test2@james.org");

        MailAddress reversePath = testee.getReversePath();

        MailAddress expectedMailAddress = SpecialAddress.UNALTERED;
        assertThat(reversePath).isEqualTo(expectedMailAddress);
    }
}
