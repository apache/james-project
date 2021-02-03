package org.apache.james.modules.protocols;

import org.apache.james.events.EventBus;
import org.apache.james.jmap.InjectionKeys;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class JmapEventBusModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(EventBus.class).annotatedWith(Names.named(InjectionKeys.JMAP)).to(EventBus.class);
    }
}
