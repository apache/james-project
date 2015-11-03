package org.apache.james.modules.server;

import org.apache.james.utils.ConfigurationPerformer;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class ConfigurationPerformerModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class);
    }
    
}
