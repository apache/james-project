package org.apache.james.mailbox.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

public class ClusterWithKeyspaceCreatedFactory {

    private final static int DEFAULT_REPLICATION_FACTOR = 1;

    public static Cluster clusterWithInitializedKeyspace(Cluster cluster, String keyspace, int replicationFactor) {
        if (isKeyspacePresent(cluster, keyspace)) {
            createKeyspace(cluster, keyspace, replicationFactor);
        }
        return cluster;
    }

    public static Cluster clusterWithInitializedKeyspace(Cluster cluster, String keyspace) {
        return clusterWithInitializedKeyspace(cluster, keyspace, DEFAULT_REPLICATION_FACTOR);
    }

    private static boolean isKeyspacePresent(Cluster cluster, String keyspace) {
        return cluster.getMetadata().getKeyspace(keyspace) == null;
    }

    private static void createKeyspace(Cluster cluster, String keyspace, int replicationFactor) {
        try (Session session = cluster.connect()) {
            session.execute("CREATE KEYSPACE IF NOT EXISTS " + keyspace
                + " WITH replication = {'class':'SimpleStrategy', 'replication_factor':" + replicationFactor + "};");
        }
    }

}
