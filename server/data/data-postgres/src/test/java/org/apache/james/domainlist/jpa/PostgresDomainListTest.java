package org.apache.james.domainlist.jpa;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.lib.DomainListContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresDomainListTest implements DomainListContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresDomainModule.MODULE);

    PostgresDomainList domainList;

    @BeforeEach
    public void setup() throws Exception {
        domainList = new PostgresDomainList(getDNSServer("localhost"), postgresExtension.getPostgresExecutor());
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .build());
    }

    @Override
    public DomainList domainList() {
        return domainList;
    }
}
