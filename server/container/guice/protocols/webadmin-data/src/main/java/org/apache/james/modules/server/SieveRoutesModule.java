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

package org.apache.james.modules.server;

import org.apache.james.mailbox.quota.task.RecomputeSieveUploadCurrentQuotasService;
import org.apache.james.mailbox.quota.task.RecomputeSingleComponentCurrentQuotasService;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.routes.SieveQuotaRoutes;
import org.apache.james.webadmin.routes.SieveScriptRoutes;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class SieveRoutesModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), Routes.class)
            .addBinding()
            .to(SieveQuotaRoutes.class);

        Multibinder.newSetBinder(binder(), Routes.class)
            .addBinding()
            .to(SieveScriptRoutes.class);

        Multibinder.newSetBinder(binder(), RecomputeSingleComponentCurrentQuotasService.class)
            .addBinding()
            .to(RecomputeSieveUploadCurrentQuotasService.class);
    }
}
