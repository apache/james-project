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

package org.apache.james.core.quota;

import org.apache.james.core.quota.quotacomponent.JmapUploadQuotaComponent;
import org.apache.james.core.quota.quotacomponent.MailBoxQuotaComponent;
import org.apache.james.core.quota.quotacomponent.SieveQuotaComponent;
import org.apache.james.core.quota.quotascope.DomainQuotaScope;
import org.apache.james.core.quota.quotascope.GlobalQuotaScope;
import org.apache.james.core.quota.quotascope.UserQuotaScope;
import org.apache.james.core.quota.quotatype.CountQuotaType;
import org.apache.james.core.quota.quotatype.SizeQuotaType;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class QuotaModelModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), QuotaComponent.class).addBinding().toInstance(MailBoxQuotaComponent.INSTANCE);
        Multibinder.newSetBinder(binder(), QuotaComponent.class).addBinding().toInstance(SieveQuotaComponent.INSTANCE);
        Multibinder.newSetBinder(binder(), QuotaComponent.class).addBinding().toInstance(JmapUploadQuotaComponent.INSTANCE);

        Multibinder.newSetBinder(binder(), QuotaType.class).addBinding().toInstance(CountQuotaType.INSTANCE);
        Multibinder.newSetBinder(binder(), QuotaType.class).addBinding().toInstance(SizeQuotaType.INSTANCE);

        Multibinder.newSetBinder(binder(), QuotaScope.class).addBinding().toInstance(UserQuotaScope.INSTANCE);
        Multibinder.newSetBinder(binder(), QuotaScope.class).addBinding().toInstance(DomainQuotaScope.INSTANCE);
        Multibinder.newSetBinder(binder(), QuotaScope.class).addBinding().toInstance(GlobalQuotaScope.INSTANCE);

        bind(QuotaComponentFactory.class).in(Scopes.SINGLETON);
        bind(QuotaTypeFactory.class).in(Scopes.SINGLETON);
        bind(QuotaScopeFactory.class).in(Scopes.SINGLETON);
    }

}
