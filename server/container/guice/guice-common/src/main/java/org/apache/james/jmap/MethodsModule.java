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
import org.apache.james.jmap.methods.JmapRequestParser;
import org.apache.james.jmap.methods.JmapRequestParserImpl;
import org.apache.james.jmap.methods.JmapResponseWriter;
import org.apache.james.jmap.methods.JmapResponseWriterImpl;
import org.apache.james.jmap.methods.Method;
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
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.utils.GuiceGenericType;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class MethodsModule<Id extends MailboxId> extends AbstractModule {

    private final GuiceGenericType<Id> guiceGenericType;

    public MethodsModule(TypeLiteral<Id> type) {
        this.guiceGenericType = new GuiceGenericType<>(type);
    }

    @Override
    protected void configure() {
        bind(JmapRequestParser.class).to(JmapRequestParserImpl.class).in(Singleton.class);
        bind(JmapResponseWriter.class).to(JmapResponseWriterImpl.class).in(Singleton.class);
        bind(ObjectMapperFactory.class).in(Singleton.class);

        bindConstant().annotatedWith(Names.named(GetMessageListMethod.MAXIMUM_LIMIT)).to(GetMessageListMethod.DEFAULT_MAXIMUM_LIMIT);

        Multibinder<Method> methods = Multibinder.newSetBinder(binder(), Method.class);
        methods.addBinding().to(guiceGenericType.newGenericType(GetMailboxesMethod.class));
        methods.addBinding().to(guiceGenericType.newGenericType(GetMessageListMethod.class));
        methods.addBinding().to(guiceGenericType.newGenericType(GetMessagesMethod.class));
        methods.addBinding().to(guiceGenericType.newGenericType(SetMessagesMethod.class));
        methods.addBinding().to(guiceGenericType.newGenericType(SetMailboxesMethod.class));

        Multibinder<SetMailboxesProcessor<Id>> setMailboxesProcessor =
            Multibinder.newSetBinder(binder(), guiceGenericType.newGenericType(SetMailboxesProcessor.class));
        setMailboxesProcessor.addBinding().to(guiceGenericType.newGenericType(SetMailboxesCreationProcessor.class));
        setMailboxesProcessor.addBinding().to(guiceGenericType.newGenericType(SetMailboxesUpdateProcessor.class));
        setMailboxesProcessor.addBinding().to(guiceGenericType.newGenericType(SetMailboxesDestructionProcessor.class));

        Multibinder<SetMessagesProcessor<Id>> setMessagesProcessors =
                Multibinder.newSetBinder(binder(), guiceGenericType.newGenericType(SetMessagesProcessor.class));
        setMessagesProcessors.addBinding().to(guiceGenericType.newGenericType(SetMessagesUpdateProcessor.class));
        setMessagesProcessors.addBinding().to(guiceGenericType.newGenericType(SetMessagesCreationProcessor.class));
        setMessagesProcessors.addBinding().to(guiceGenericType.newGenericType(SetMessagesDestructionProcessor.class));
    }

}
