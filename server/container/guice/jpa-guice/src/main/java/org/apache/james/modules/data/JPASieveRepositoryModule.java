package org.apache.james.modules.data;

import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.file.SieveFileRepository;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class JPASieveRepositoryModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(SieveFileRepository.class).in(Scopes.SINGLETON);

        bind(SieveRepository.class).to(SieveFileRepository.class);
    }

}
