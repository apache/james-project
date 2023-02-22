package org.apache.james.user.ldap;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.healthcheck.Result;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LdapHealthCheckTest {

    LdapHealthCheck ldapHealthCheck;
    ReadOnlyUsersLDAPRepository ldapUserRepository;
    LdapGenericContainer ldapContainer;

    @BeforeEach
    public void setUp() {
        ldapUserRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
        ldapHealthCheck = new LdapHealthCheck(ldapUserRepository);
        ldapContainer = DockerLdapSingleton.ldapContainer;
    }

    @Test
    void checkShouldReturnUnhealthyIfLdapIsDown() {
        ldapContainer.pause();

        Result checkResult = ldapHealthCheck.check().block();
        assertThat(checkResult.isUnHealthy()).isTrue();

        ldapContainer.unpause();
    }

    @Test
    void checkShouldReturnHealthyIfLdapIsRunning() {
        Result checkResult = ldapHealthCheck.check().block();
        assertThat(checkResult.isHealthy()).isTrue();
    }
}
