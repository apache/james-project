package org.apache.james.user.cassandra;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.user.cassandra.LocalPartLookupModule.Table.LOCALPART;
import static org.apache.james.user.cassandra.LocalPartLookupModule.Table.TABLE_NAME;
import static org.apache.james.user.cassandra.LocalPartLookupModule.Table.USERNAME;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.user.lib.LocalPart;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraLocalPartLookupRepository {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final PreparedStatement delete;

    @Inject
    public CassandraLocalPartLookupRepository(Session session) {
        executor = new CassandraAsyncExecutor(session);

        insert = session.prepare(insertInto(TABLE_NAME)
            .value(USERNAME, bindMarker(USERNAME))
            .value(LOCALPART, bindMarker(LOCALPART)));
        select = session.prepare(select()
            .from(TABLE_NAME)
            .where(eq(LOCALPART, bindMarker(LOCALPART))));
        delete = session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME)
            .where(eq(LOCALPART, bindMarker(LOCALPART)))
            .and(eq(USERNAME, bindMarker(USERNAME))));
    }

    public Flux<Username> retrieve(LocalPart localPart) {
        return executor.executeRows(select.bind()
            .setString(LOCALPART, localPart.asString()))
            .map(this::fromRow);
    }

    public Mono<Void> store(Username username) {
        return executor.executeVoid(insert.bind()
            .setString(LOCALPART, username.getLocalPart())
            .setString(USERNAME, username.asString()));
    }


    public Mono<Void> delete(Username username) {
        return executor.executeVoid(delete.bind()
            .setString(LOCALPART, username.getLocalPart())
            .setString(USERNAME, username.asString()));
    }

    private Username fromRow(Row row) {
        return Username.of(row.getString(USERNAME));
    }
}
