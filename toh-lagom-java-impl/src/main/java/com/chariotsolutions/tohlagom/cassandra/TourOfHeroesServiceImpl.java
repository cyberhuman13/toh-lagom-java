package com.chariotsolutions.tohlagom.cassandra;

import java.util.List;
import javax.inject.Inject;
import java.util.stream.Collectors;
import java.util.concurrent.CompletionStage;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import com.chariotsolutions.tohlagom.api.Hero;

public class TourOfHeroesServiceImpl extends com.chariotsolutions.tohlagom.common.TourOfHeroesServiceImpl {
    private final CassandraSession session;

    @Inject
    public TourOfHeroesServiceImpl(ClusterSharding clusterSharding,
                                   ReadSide readSide, CassandraSession session) {
        super(clusterSharding);
        this.session = session;
        readSide.register(HeroEventProcessor.class);
    }

    @Override
    protected CompletionStage<List<Hero>> performRead(String query) {
        return session.selectAll(query).thenApply(rows ->
                rows.stream().map(row -> new Hero(
                        row.getString(com.chariotsolutions.tohlagom.common.HeroEventProcessor.ID_COLUMN),
                        row.getString(com.chariotsolutions.tohlagom.common.HeroEventProcessor.NAME_COLUMN)
                )).collect(Collectors.toList())
        );
    }
}
