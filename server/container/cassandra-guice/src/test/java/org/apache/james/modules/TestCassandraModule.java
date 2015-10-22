package org.apache.james.modules;

import org.apache.james.mailbox.cassandra.CassandraClusterSingleton;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.google.inject.AbstractModule;

public class TestCassandraModule extends AbstractModule {
    
    @Override
    protected void configure() {
        bind(Session.class).toInstance(CassandraClusterSingleton.build().getConf());
        bind(Cluster.class).toInstance(CassandraClusterSingleton.build().getCluster());
    }
}