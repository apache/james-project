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

package org.apache.james.jmap;

import org.apache.james.jmap.core.CapabilityFactory;
import org.apache.james.jmap.core.JmapRfc8621Configuration;
import org.apache.james.jmap.pushsubscription.PushClientConfiguration;
import org.apache.james.jmap.pushsubscription.VapidCapabilityFactory;
import org.apache.james.jmap.vapid.SecurityKeyLoader;
import org.apache.james.jmap.vapid.SignatureHandler;
import org.apache.james.jmap.vapid.VapidSignatureHandler;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoSet;

public class JMAPVapidModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(VapidSignatureHandler.class).in(Scopes.SINGLETON);
        bind(SecurityKeyLoader.class).in(Scopes.SINGLETON);

        bind(SignatureHandler.class).to(VapidSignatureHandler.class);
    }


    @ProvidesIntoSet
    CapabilityFactory vapidCapability(PushClientConfiguration configuration) {
        return new VapidCapabilityFactory(configuration);
    }
}
