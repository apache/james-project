package org.apache.james.user.ldap;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class LdapHealthCheck implements HealthCheck {
    public static final ComponentName COMPONENT_NAME = new ComponentName("LDAP User Server");
    public static final Username LDAP_TEST_USER = Username.of("ldap-test");
    private static final Logger LOGGER = LoggerFactory.getLogger(LdapHealthCheck.class);

    private final ReadOnlyUsersLDAPRepository ldapUserRepository;

    @Inject
    public LdapHealthCheck(ReadOnlyUsersLDAPRepository ldapUserRepository) {
        this.ldapUserRepository = ldapUserRepository;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> check() {
        return Mono.fromCallable(() -> ldapUserRepository.getUserByName(LDAP_TEST_USER))
            .map(user -> Result.healthy(COMPONENT_NAME))
            .onErrorResume(e -> Mono.just(Result.unhealthy(COMPONENT_NAME, "Error checking LDAP server!", e)))
            .doOnError(e -> LOGGER.error("Error in LDAP server", e));
    }
}
