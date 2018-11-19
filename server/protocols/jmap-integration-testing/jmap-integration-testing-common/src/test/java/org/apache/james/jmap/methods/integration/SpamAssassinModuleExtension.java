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

package org.apache.james.jmap.methods.integration;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.spamassassin.SpamAssassinExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;

public class SpamAssassinModuleExtension implements GuiceModuleTestExtension {

    private final SpamAssassinExtension spamAssassin;

    public SpamAssassinModuleExtension() {
        this.spamAssassin = new org.apache.james.spamassassin.SpamAssassinExtension();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        spamAssassin.beforeAll(extensionContext);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        spamAssassin.afterAll(extensionContext);
    }

    @Override
    public Module getModule() {
        return new SpamAssassinModule(spamAssassin);
    }

    public SpamAssassinExtension spamAssassinExtension() {
        return spamAssassin;
    }
}
