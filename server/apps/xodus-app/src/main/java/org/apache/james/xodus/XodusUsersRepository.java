package org.apache.james.xodus;

import jakarta.inject.Inject;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.lib.UsersRepositoryImpl;

public class XodusUsersRepository extends UsersRepositoryImpl<XodusUsersDAO> {
    @Inject
    public XodusUsersRepository(DomainList domainList, XodusUsersDAO usersDAO) {
        super(domainList, usersDAO);
    }
}
