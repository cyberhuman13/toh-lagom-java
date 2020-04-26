package com.chariotsolutions.tohlagom.common;

import java.util.List;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;

import akka.Done;
import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import com.chariotsolutions.tohlagom.impl.Confirmation;
import com.chariotsolutions.tohlagom.impl.HeroAggregate;
import com.chariotsolutions.tohlagom.impl.HeroCommand;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.BadRequest;
import static com.chariotsolutions.tohlagom.common.Helpers.*;
import com.chariotsolutions.tohlagom.impl.Confirmation.*;
import com.chariotsolutions.tohlagom.impl.HeroCommand.*;
import com.chariotsolutions.tohlagom.api.*;

public abstract class TourOfHeroesServiceImpl implements TourOfHeroesService {
    private final ClusterSharding clusterSharding;

    @Inject
    public TourOfHeroesServiceImpl(ClusterSharding clusterSharding) {
        this.clusterSharding = clusterSharding;

        // register the Aggregate as a sharded entity
        this.clusterSharding.init(Entity.of(
                HeroAggregate.ENTITY_TYPE_KEY,
                HeroAggregate::create
        ));
    }

    protected abstract CompletionStage<List<Hero>> performRead(String query);

    private EntityRef<HeroCommand> getEntityRef(String id) {
        return clusterSharding.entityRefFor(HeroAggregate.ENTITY_TYPE_KEY, id);
    }

    @Override
    public ServiceCall<NotUsed, List<Hero>> heroes() {
        return __ -> performRead("SELECT * FROM " + HeroEventProcessor.TABLE);
    }

    @Override
    public ServiceCall<NotUsed, List<Hero>> search(String name) {
        return __ -> performRead("SELECT * FROM " + HeroEventProcessor.TABLE +
                " WHERE " + HeroEventProcessor.NAME_COLUMN + " LIKE '" + name.toLowerCase() + "%'");
    }

    @Override
    public ServiceCall<NotUsed, Hero> fetchHero(String id) {
        return __ -> {
            String heroId = convertId(id);
            return performRead("SELECT * FROM " + HeroEventProcessor.TABLE +
                    " WHERE " + HeroEventProcessor.ID_COLUMN + " = '" + heroId + "'")
                    .thenApply(heroes -> heroes.get(0));
        };
    }

    @Override
    public ServiceCall<NewHero, Hero> createHero() {
        return hero -> {
            String heroId = newId();
            return getEntityRef(heroId).
                    <Confirmation>ask(replyTo -> new CreateHero(hero.name, replyTo), askTimeout)
                    .thenApply(confirmation -> {
                        if (confirmation instanceof Accepted) {
                            return new Hero(heroId, toTitleCase(hero.name));
                        } else {
                            throw new BadRequest("Failed to create a hero with name " + hero.name + ".");
                        }
                    });
        };
    }

    @Override
    public ServiceCall<Hero, Done> changeHero() {
        return hero -> {
            String heroId = convertId(hero.id);
            return getEntityRef(heroId).
                    <Confirmation>ask(replyTo -> new ChangeHero(hero.name, replyTo), askTimeout)
                    .thenApply(confirmation -> {
                        if (confirmation instanceof Accepted) {
                            return Done.getInstance();
                        } else {
                            throw new BadRequest("Failed to change a hero with id " + heroId + ".");
                        }
                    });
        };
    }

    @Override
    public ServiceCall<NotUsed, Done> deleteHero(String id) {
        return __ -> {
            String heroId = convertId(id);
            return getEntityRef(heroId).ask(DeleteHero::new, askTimeout)
                    .thenApply(confirmation -> {
                        if (confirmation instanceof Accepted) {
                            return Done.getInstance();
                        } else {
                            throw new BadRequest("Failed to delete a hero with id " + heroId + ".");
                        }
                    });
        };
    }
}
