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

import org.apache.james.jmap.json.ObjectMapperFactory;
import org.apache.james.jmap.methods.GetMailboxesMethod;
import org.apache.james.jmap.methods.GetMessageListMethod;
import org.apache.james.jmap.methods.GetMessagesMethod;
import org.apache.james.jmap.methods.GetVacationResponseMethod;
import org.apache.james.jmap.methods.JmapRequestParser;
import org.apache.james.jmap.methods.JmapRequestParserImpl;
import org.apache.james.jmap.methods.JmapResponseWriter;
import org.apache.james.jmap.methods.JmapResponseWriterImpl;
import org.apache.james.jmap.methods.Method;
import org.apache.james.jmap.methods.SendMDNProcessor;
import org.apache.james.jmap.methods.SetMailboxesCreationProcessor;
import org.apache.james.jmap.methods.SetMailboxesDestructionProcessor;
import org.apache.james.jmap.methods.SetMailboxesMethod;
import org.apache.james.jmap.methods.SetMailboxesProcessor;
import org.apache.james.jmap.methods.SetMailboxesUpdateProcessor;
import org.apache.james.jmap.methods.SetMessagesCreationProcessor;
import org.apache.james.jmap.methods.SetMessagesDestructionProcessor;
import org.apache.james.jmap.methods.SetMessagesMethod;
import org.apache.james.jmap.methods.SetMessagesProcessor;
import org.apache.james.jmap.methods.SetMessagesUpdateProcessor;
import org.apache.james.jmap.methods.SetVacationResponseMethod;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class MethodsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(JmapRequestParserImpl.class).in(Scopes.SINGLETON);
        bind(JmapResponseWriterImpl.class).in(Scopes.SINGLETON);
        bind(ObjectMapperFactory.class).in(Scopes.SINGLETON);

        bind(JmapRequestParser.class).to(JmapRequestParserImpl.class);
        bind(JmapResponseWriter.class).to(JmapResponseWriterImpl.class);

        bindConstant().annotatedWith(Names.named(GetMessageListMethod.MAXIMUM_LIMIT)).to(GetMessageListMethod.DEFAULT_MAXIMUM_LIMIT);

        Multibinder<Method> methods = Multibinder.newSetBinder(binder(), Method.class);
        methods.addBinding().to(GetMailboxesMethod.class);
        methods.addBinding().to(GetMessageListMethod.class);
        methods.addBinding().to(GetMessagesMethod.class);
        methods.addBinding().to(SetMessagesMethod.class);
        methods.addBinding().to(SetMailboxesMethod.class);
        methods.addBinding().to(GetVacationResponseMethod.class);
        methods.addBinding().to(SetVacationResponseMethod.class);

        Multibinder<SetMailboxesProcessor> setMailboxesProcessor =
            Multibinder.newSetBinder(binder(), SetMailboxesProcessor.class);
        setMailboxesProcessor.addBinding().to(SetMailboxesCreationProcessor.class);
        setMailboxesProcessor.addBinding().to(SetMailboxesUpdateProcessor.class);
        setMailboxesProcessor.addBinding().to(SetMailboxesDestructionProcessor.class);

        Multibinder<SetMessagesProcessor> setMessagesProcessors =
                Multibinder.newSetBinder(binder(), SetMessagesProcessor.class);
        setMessagesProcessors.addBinding().to(SetMessagesUpdateProcessor.class);
        setMessagesProcessors.addBinding().to(SetMessagesCreationProcessor.class);
        setMessagesProcessors.addBinding().to(SetMessagesDestructionProcessor.class);
        setMessagesProcessors.addBinding().to(SendMDNProcessor.class);
    }

}
