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

package org.apache.james.transport.mailets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.transport.mailets.jsieve.ResourceLocator;
import org.apache.james.user.api.UsersRepository;
import org.junit.Before;
import org.junit.Test;

public class ResourceLocatorTest {

    public static final String RECEIVER_LOCALHOST = "receiver@localhost";
    public static final Username USERNAME = Username.of(RECEIVER_LOCALHOST);
    private SieveRepository sieveRepository;
    private ResourceLocator resourceLocator;
    private MailAddress mailAddress;
    private UsersRepository usersRepository;

    @Before
    public void setUp() throws Exception {
        sieveRepository = mock(SieveRepository.class);
        usersRepository = mock(UsersRepository.class);
        resourceLocator = new ResourceLocator(sieveRepository, usersRepository);
        mailAddress = new MailAddress(RECEIVER_LOCALHOST);
    }

    @Test(expected = ScriptNotFoundException.class)
    public void resourceLocatorImplShouldPropagateScriptNotFound() throws Exception {
        when(sieveRepository.getActive(USERNAME)).thenThrow(new ScriptNotFoundException());
        when(usersRepository.getUsername(mailAddress)).thenReturn(Username.of(RECEIVER_LOCALHOST));

        resourceLocator.get(mailAddress);
    }

    @Test
    public void resourceLocatorImplShouldWork() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);
        when(sieveRepository.getActive(USERNAME)).thenReturn(inputStream);
        when(usersRepository.getUsername(mailAddress)).thenReturn(Username.of(RECEIVER_LOCALHOST));

        assertThat(resourceLocator.get(mailAddress).getScriptContent()).isEqualTo(inputStream);
    }
}
