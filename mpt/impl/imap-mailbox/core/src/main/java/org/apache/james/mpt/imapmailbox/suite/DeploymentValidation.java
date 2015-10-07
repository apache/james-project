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

package org.apache.james.mpt.imapmailbox.suite;

import javax.inject.Inject;
import java.util.Locale;

import org.apache.james.mpt.api.HostSystem;
import org.apache.james.mpt.imapmailbox.suite.base.BaseImapProtocol;
import org.junit.Test;

public class DeploymentValidation extends BaseImapProtocol {

    public static final String DOMAIN = "domain";
    public static final String USER = "imapuser@" + DOMAIN;

    @Inject
    private static HostSystem system;

    public DeploymentValidation() throws Exception {
        super(system);
    }

    @Override
    public void setUp() throws Exception {

    }

    @Test
    public void validateDeployment() throws Exception {
        scriptTest("ValidateDeployment", Locale.US);
    }

}
