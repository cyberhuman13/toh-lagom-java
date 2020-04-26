package com.chariotsolutions.tohlagom.impl;

import java.util.UUID;
import akka.actor.typed.ActorRef;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import static com.chariotsolutions.tohlagom.common.Helpers.*;
import com.chariotsolutions.tohlagom.impl.Confirmation.*;
import com.chariotsolutions.tohlagom.impl.HeroCommand.*;
import org.junit.ClassRule;
import org.junit.Test;

public class HeroAggregateTest {
    private static final String inmemConfig =
        "akka.persistence.journal.plugin = \"akka.persistence.journal.inmem\" \n";

    private static final String snapshotConfig =
        "akka.persistence.snapshot-store.plugin = \"akka.persistence.snapshot-store.local\" \n"
            + "akka.persistence.snapshot-store.local.dir = \"target/snapshot-"
            + UUID.randomUUID().toString()
            + "\" \n";

    private static final String config = inmemConfig + snapshotConfig;

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(config);

    @Test
    public void testCreateHero() {
        final String name = "aLiCe";
        final String heroId = newId();

        final ActorRef<HeroCommand> ref = testKit.spawn(HeroAggregate.create(
                // Unit testing the Aggregate requires an EntityContext but starting
                // a complete Akka Cluster or sharding the actors is not requried.
                // The actorRef to the shard can be null as it won't be used.
                new EntityContext<>(HeroAggregate.ENTITY_TYPE_KEY, heroId,  null)));
        final TestProbe<Confirmation> confirmationProbe = testKit.createTestProbe(Confirmation.class);
        final TestProbe<HeroState> stateProbe = testKit.createTestProbe(HeroState.class);

        ref.tell(new CreateHero(name, confirmationProbe.getRef()));
        confirmationProbe.expectMessageClass(Accepted.class);

        ref.tell(new FetchHero(stateProbe.getRef()));
        stateProbe.expectMessage(new HeroState(name, true));
    }

    @Test
    public void testChangeExistingHero() {
        final String oldName = "aLiCe";
        final String newName = "bOrIs";
        final String heroId = newId();

        final ActorRef<HeroCommand> ref = testKit.spawn(HeroAggregate.create(
                new EntityContext<>(HeroAggregate.ENTITY_TYPE_KEY, heroId,  null)));
        final TestProbe<Confirmation> confirmationProbe = testKit.createTestProbe(Confirmation.class);
        final TestProbe<HeroState> stateProbe = testKit.createTestProbe(HeroState.class);

        ref.tell(new CreateHero(oldName, confirmationProbe.getRef()));
        confirmationProbe.expectMessageClass(Accepted.class);

        ref.tell(new ChangeHero(newName, confirmationProbe.getRef()));
        confirmationProbe.expectMessageClass(Accepted.class);

        ref.tell(new FetchHero(stateProbe.getRef()));
        stateProbe.expectMessage(new HeroState(newName, true));
    }

    @Test
    public void testChangeNonExistingHero() {
        final String newName = "bOrIs";
        final String heroId = newId();

        final ActorRef<HeroCommand> ref = testKit.spawn(HeroAggregate.create(
                new EntityContext<>(HeroAggregate.ENTITY_TYPE_KEY, heroId,  null)));
        final TestProbe<Confirmation> confirmationProbe = testKit.createTestProbe(Confirmation.class);

        ref.tell(new ChangeHero(newName, confirmationProbe.getRef()));
        confirmationProbe.expectMessage(new Rejected("The hero is in invalid state."));
    }

    @Test
    public void testDeleteExistingHero() {
        final String name = "aLiCe";
        final String heroId = newId();

        final ActorRef<HeroCommand> ref = testKit.spawn(HeroAggregate.create(
                new EntityContext<>(HeroAggregate.ENTITY_TYPE_KEY, heroId,  null)));
        final TestProbe<Confirmation> confirmationProbe = testKit.createTestProbe(Confirmation.class);
        final TestProbe<HeroState> stateProbe = testKit.createTestProbe(HeroState.class);

        ref.tell(new CreateHero(name, confirmationProbe.getRef()));
        confirmationProbe.expectMessageClass(Accepted.class);

        ref.tell(new DeleteHero(confirmationProbe.getRef()));
        confirmationProbe.expectMessageClass(Accepted.class);

        ref.tell(new FetchHero(stateProbe.getRef()));
        stateProbe.expectNoMessage(askTimeout);
    }

    @Test
    public void testDeleteAlreadyDeletedHero() {
        final String name = "aLiCe";
        final String heroId = newId();

        final ActorRef<HeroCommand> ref = testKit.spawn(HeroAggregate.create(
                new EntityContext<>(HeroAggregate.ENTITY_TYPE_KEY, heroId,  null)));
        final TestProbe<Confirmation> confirmationProbe = testKit.createTestProbe(Confirmation.class);

        ref.tell(new CreateHero(name, confirmationProbe.getRef()));
        confirmationProbe.expectMessageClass(Accepted.class);

        ref.tell(new DeleteHero(confirmationProbe.getRef()));
        confirmationProbe.expectMessageClass(Accepted.class);

        ref.tell(new DeleteHero(confirmationProbe.getRef()));
        confirmationProbe.expectMessage(new Rejected("The hero has already been deleted."));
    }
}
