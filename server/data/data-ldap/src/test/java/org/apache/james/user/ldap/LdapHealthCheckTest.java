package org.apache.james.user.ldap;

import org.apache.james.core.healthcheck.Result;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertNotNull(checkResult);
        assertTrue(checkResult.isUnHealthy());

        ldapContainer.unpause();
    }

    @Test
    void checkShouldReturnHealthyIfLdapIsRunning() {
        Result checkResult = ldapHealthCheck.check().block();
        assertNotNull(checkResult);
        assertTrue(checkResult.isHealthy());
    }
}
