package org.apache.james.modules.blobstore.validation;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.lifecycle.api.StartUpCheck;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

public class StoragePolicyConfigurationSanityEnforcementModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<EventDTOModule<? extends Event, ? extends EventDTO>> eventDTOModuleBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<EventDTOModule<? extends Event, ? extends EventDTO>>() {});
        eventDTOModuleBinder.addBinding().toInstance(StorageStrategyModule.STORAGE_STRATEGY);

        bind(EventsourcingStorageStrategy.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), StartUpCheck.class)
            .addBinding()
            .to(BlobStoreConfigurationValidationStartUpCheck.class);
    }
}
