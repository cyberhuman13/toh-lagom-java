package com.chariotsolutions.tohlagom.mixed;

import java.util.List;
import java.sql.ResultSet;
import javax.inject.Inject;
import java.sql.PreparedStatement;
import java.util.concurrent.CompletionStage;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import com.lightbend.lagom.javadsl.persistence.jdbc.JdbcSession;
import com.chariotsolutions.tohlagom.api.Hero;
import org.pcollections.TreePVector;
import org.pcollections.PSequence;

public class TourOfHeroesServiceImpl extends com.chariotsolutions.tohlagom.common.TourOfHeroesServiceImpl {
    private final JdbcSession session;

    @Inject
    public TourOfHeroesServiceImpl(ClusterSharding clusterSharding,
                                   ReadSide readSide, JdbcSession session) {
        super(clusterSharding);
        this.session = session;
        readSide.register(HeroEventProcessor.class);
    }

    @Override
    protected CompletionStage<List<Hero>> performRead(String query) {
        return session.withConnection(connection -> {
            try (final PreparedStatement ps = connection.prepareStatement(query)) {
                try (final ResultSet rs = ps.executeQuery()) {
                    PSequence<Hero> heroes = TreePVector.empty();

                    while (rs.next()) {
                        heroes = heroes.plus(new Hero(
                                rs.getString(com.chariotsolutions.tohlagom.common.HeroEventProcessor.ID_COLUMN),
                                rs.getString(com.chariotsolutions.tohlagom.common.HeroEventProcessor.NAME_COLUMN)
                        ));
                    }

                    return heroes;
                }
            }
        });
    }
}
