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
package org.apache.james;

import java.util.Arrays;
import java.util.Collection;

import org.apache.james.utils.JmapGuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;

import com.google.common.collect.Iterables;
import com.google.inject.Module;
import com.google.inject.util.Modules;

public class JmapJamesServer extends GuiceJamesServerImpl implements GuiceJamesServer, JmapServer, WebAdminServer {

    public JmapJamesServer() {
        super();
    }

    private JmapJamesServer(Module module) {
        super(module);
    }

    public JmapJamesServer combineWith(Module... modules) {
        return new JmapJamesServer(Modules.combine(Iterables.concat(Arrays.asList(module), Arrays.asList(modules))));
    }

    public JmapJamesServer overrideWith(Module... overrides) {
        return overrideWith(Arrays.asList(overrides));
    }

    public JmapJamesServer overrideWith(Collection<Module> overrides) {
        return new JmapJamesServer(Modules.override(module).with(overrides));
    }

    @Override
    public JmapGuiceProbe getJmapProbe() {
        return getGuiceProbeProvider().getProbe(JmapGuiceProbe.class);
    }

    @Override
    public WebAdminGuiceProbe getWebAdminProbe() {
        return getGuiceProbeProvider().getProbe(WebAdminGuiceProbe.class);
    }


}
