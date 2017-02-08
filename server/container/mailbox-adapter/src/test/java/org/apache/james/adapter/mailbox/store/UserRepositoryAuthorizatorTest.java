package org.apache.james.adapter.mailbox.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserRepositoryAuthorizatorTest {
    private static final String ADMIN = "admin";
    private static final String USER = "user";
    private static final Logger LOGGER = LoggerFactory.getLogger(UserRepositoryAuthorizatorTest.class);

    private UsersRepository usersRepository;
    private UserRepositoryAuthorizator testee;

    @Before
    public void setUp() throws Exception {
        usersRepository = mock(UsersRepository.class);
        testee = new UserRepositoryAuthorizator(usersRepository);
        testee.setLog(LOGGER);
    }

    @Test
    public void canLoginAsOtherUserShouldReturnFalseWhenIsAdministratorThrows() throws Exception {
        when(usersRepository.isAdministrator(ADMIN))
            .thenThrow(new UsersRepositoryException("expected error"));
        
        assertThat(testee.canLoginAsOtherUser(ADMIN, USER)).isFalse();
    }

    @Test
    public void canLoginAsOtherUserShouldReturnFalseWhenIsAdministratorReturnFalse() throws Exception {
        when(usersRepository.isAdministrator(ADMIN))
            .thenReturn(false);
        
        assertThat(testee.canLoginAsOtherUser(ADMIN, USER)).isFalse();
    }

    @Test
    public void canLoginAsOtherUserShouldReturnFalseWhenUserIsNotInRepository() throws Exception {
        when(usersRepository.isAdministrator(ADMIN))
            .thenReturn(true);
        when(usersRepository.contains(USER))
            .thenReturn(false);
        
        assertThat(testee.canLoginAsOtherUser(ADMIN, USER)).isFalse();
    }

    @Test
    public void canLoginAsOtherUserShouldReturnTrueWhenAdminAndUserIsInRepository() throws Exception {
        when(usersRepository.isAdministrator(ADMIN))
            .thenReturn(true);
        when(usersRepository.contains(USER))
            .thenReturn(true);
        
        assertThat(testee.canLoginAsOtherUser(ADMIN, USER)).isTrue();
    }

}
