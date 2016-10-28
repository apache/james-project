package org.apache.james.modules.data;

import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.jmap.vacation.NoneVacationRepository;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.file.SieveFileRepository;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class JPADataJmapModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(NoneVacationRepository.class).in(Scopes.SINGLETON);
        bind(VacationRepository.class).to(NoneVacationRepository.class);

        bind(SieveFileRepository.class).in(Scopes.SINGLETON);
        bind(SieveRepository.class).to(SieveFileRepository.class);
    }

}
