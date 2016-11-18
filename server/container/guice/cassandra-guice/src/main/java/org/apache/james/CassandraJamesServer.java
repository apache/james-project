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

import org.apache.james.utils.JmapGuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;

import com.google.common.collect.Iterables;
import com.google.inject.Module;
import com.google.inject.util.Modules;

public class CassandraJamesServer extends GuiceJamesServerImpl implements JmapJamesServer {

    public CassandraJamesServer() {
        super();
    }

    public CassandraJamesServer(Module module) {
        super(module);
    }

    public CassandraJamesServer combineWith(Module... modules) {
        return new CassandraJamesServer(Modules.combine(Iterables.concat(Arrays.asList(module), Arrays.asList(modules))));
    }

    public CassandraJamesServer overrideWith(Module... overrides) {
        return new CassandraJamesServer(Modules.override(module).with(overrides));
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
