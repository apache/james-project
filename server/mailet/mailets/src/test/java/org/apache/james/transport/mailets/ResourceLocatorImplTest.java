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

import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class ResourceLocatorImplTest {

    private SieveRepository sieveRepository;
    private ResourceLocatorImpl resourceLocator;

    @Before
    public void setUp() {
        sieveRepository = mock(SieveRepository.class);
        resourceLocator = new ResourceLocatorImpl(true, sieveRepository);
    }

    @Test(expected = ScriptNotFoundException.class)
    public void resourceLocatorImplShouldPropagateScriptNotFound() throws Exception {
        when(sieveRepository.getActive("receiver@localhost")).thenThrow(new ScriptNotFoundException());
        resourceLocator.get("//receiver@localhost/sieve");
    }

    @Test
    public void resourceLocatorImplShouldWork() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);
        when(sieveRepository.getActive("receiver@localhost")).thenReturn(inputStream);
        assertThat(resourceLocator.get("//receiver@localhost/sieve")).isEqualTo(inputStream);
    }
}
