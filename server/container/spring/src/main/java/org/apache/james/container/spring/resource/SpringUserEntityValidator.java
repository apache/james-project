package org.apache.james.container.spring.resource;

import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.apache.james.DefaultUserEntityValidator;
import org.apache.james.RecipientRewriteTableUserEntityValidator;
import org.apache.james.UserEntityValidator;
import org.apache.james.core.Username;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.user.api.UsersRepository;

public class SpringUserEntityValidator implements UserEntityValidator {
    private UsersRepository usersRepository;
    private RecipientRewriteTable rrt;
    private UserEntityValidator delegate;

    @Inject
    public void setUsersRepository(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
        if (rrt != null) {
            delegate = UserEntityValidator.aggregate(
                new DefaultUserEntityValidator(usersRepository),
                new RecipientRewriteTableUserEntityValidator(rrt));
        }
    }

    @Inject
    public void setRrt(RecipientRewriteTable rrt) {
        this.rrt = rrt;
        if (usersRepository != null) {
            delegate = UserEntityValidator.aggregate(
                new DefaultUserEntityValidator(usersRepository),
                new RecipientRewriteTableUserEntityValidator(rrt));
        }
    }

    @Override
    public Optional<ValidationFailure> canCreate(Username username, Set<EntityType> ignoredTypes) throws Exception {
        return delegate.canCreate(username, ignoredTypes);
    }
}
