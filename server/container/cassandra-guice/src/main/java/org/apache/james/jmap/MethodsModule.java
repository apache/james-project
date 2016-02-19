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
import org.apache.james.jmap.methods.SetMessagesCreationProcessor;
import org.apache.james.jmap.methods.SetMessagesDestructionProcessor;
import org.apache.james.jmap.methods.SetMessagesMethod;
import org.apache.james.jmap.methods.SetMessagesProcessor;
import org.apache.james.jmap.methods.SetMessagesUpdateProcessor;
import org.apache.james.mailbox.cassandra.CassandraId;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class MethodsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(JmapRequestParser.class).to(JmapRequestParserImpl.class).in(Singleton.class);
        bind(JmapResponseWriter.class).to(JmapResponseWriterImpl.class).in(Singleton.class);
        bind(ObjectMapperFactory.class).in(Singleton.class);

        bindConstant().annotatedWith(Names.named(GetMessageListMethod.MAXIMUM_LIMIT)).to(GetMessageListMethod.DEFAULT_MAXIMUM_LIMIT);

        Multibinder<Method> methods = Multibinder.newSetBinder(binder(), Method.class);
        methods.addBinding().to(new TypeLiteral<GetMailboxesMethod<CassandraId>>(){});
        methods.addBinding().to(new TypeLiteral<GetMessageListMethod<CassandraId>>(){});
        methods.addBinding().to(new TypeLiteral<GetMessagesMethod<CassandraId>>(){});
        methods.addBinding().to(new TypeLiteral<SetMessagesMethod<CassandraId>>(){});

        Multibinder<SetMessagesProcessor<CassandraId>> setMessagesProcessors = Multibinder.newSetBinder(binder(), new TypeLiteral<SetMessagesProcessor<CassandraId>>(){});
        setMessagesProcessors.addBinding().to(new TypeLiteral<SetMessagesUpdateProcessor<CassandraId>>(){});
        setMessagesProcessors.addBinding().to(new TypeLiteral<SetMessagesCreationProcessor<CassandraId>>(){});
        setMessagesProcessors.addBinding().to(new TypeLiteral<SetMessagesDestructionProcessor<CassandraId>>(){});
    }
}
