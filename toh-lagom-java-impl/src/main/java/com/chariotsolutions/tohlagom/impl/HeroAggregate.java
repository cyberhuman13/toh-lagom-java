package com.chariotsolutions.tohlagom.impl;

import java.util.Set;

import akka.persistence.typed.EventAdapter;
import akka.persistence.typed.javadsl.*;
import akka.persistence.typed.PersistenceId;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import com.chariotsolutions.tohlagom.impl.Confirmation.*;
import com.chariotsolutions.tohlagom.impl.HeroCommand.*;
import com.chariotsolutions.tohlagom.impl.HeroEvent.*;

/**
 * This is an event sourced aggregate. It has a state, {@link HeroState}.
 * <p>
 * Event sourced aggregate are interacted with by sending them commands.
 * Commands may emit events, and it's the events that get persisted.
 * Each event will have an event handler registered for it, and an
 * event handler simply applies an event to the current state. This will be done
 * when the event is first created, and it will also be done when the entity is
 * loaded from the database - each event will be replayed to recreate the state
 * of the aggregate.
 */
public class HeroAggregate extends EventSourcedBehaviorWithEnforcedReplies<HeroCommand, HeroEvent, HeroState> {
    public static final EntityTypeKey<HeroCommand> ENTITY_TYPE_KEY =
        EntityTypeKey.create(HeroCommand.class, "HeroAggregate");
    private final EntityContext<HeroCommand> entityContext;

    HeroAggregate(EntityContext<HeroCommand> entityContext) {
        super(PersistenceId.of(
                entityContext.getEntityTypeKey().name(),
                entityContext.getEntityId()));
        this.entityContext = entityContext;
    }

    public static HeroAggregate create(EntityContext<HeroCommand> entityContext) {
        return new HeroAggregate(entityContext);
    }

    @Override
    public HeroState emptyState() {
        return HeroState.INITIAL;
    }

    @Override
    public CommandHandlerWithReply<HeroCommand, HeroEvent, HeroState> commandHandler() {
        final CommandHandlerWithReplyBuilder<HeroCommand, HeroEvent, HeroState> builder =
                newCommandHandlerWithReplyBuilder();

        builder.forAnyState()
                .onCommand(CreateHero.class, (state, cmd) -> state.valid
                        ? Effect().reply(cmd.replyTo, new Rejected("The hero already exists."))
                        : Effect().persist(new HeroCreated(entityContext.getEntityId(), cmd.name))
                        .thenReply(cmd.replyTo, __ -> new Confirmation.Accepted()))
                .onCommand(ChangeHero.class, (state, cmd) -> !state.valid
                        ? Effect().reply(cmd.replyTo, new Rejected("The hero is in invalid state."))
                        : Effect().persist(new HeroChanged(entityContext.getEntityId(), cmd.newName, state.name))
                        .thenReply(cmd.replyTo, __ -> new Confirmation.Accepted()))
                .onCommand(DeleteHero.class, (state, cmd) -> !state.valid
                        ? Effect().reply(cmd.replyTo, new Rejected("The hero has already been deleted."))
                        : Effect().persist(new HeroDeleted(entityContext.getEntityId()))
                        .thenReply(cmd.replyTo, __ -> new Confirmation.Accepted()))
                .onCommand(FetchHero.class, (state, cmd) -> state.valid
                        ? Effect().reply(cmd.replyTo, state) : Effect().noReply());

        return builder.build();
    }

    @Override
    public EventHandler<HeroState, HeroEvent> eventHandler() {
        final EventHandlerBuilder<HeroState, HeroEvent> builder = newEventHandlerBuilder();

        builder.forAnyState()
                .onEvent(HeroCreated.class, (state, event) -> new HeroState(event.name, true))
                .onEvent(HeroChanged.class, (state, event) -> new HeroState(event.newName, state.valid))
                .onEvent(HeroDeleted.class, (state, event) -> new HeroState(state.name, false));

        return builder.build();
    }

    @Override
    public EventAdapter<HeroEvent, ?> eventAdapter() {
        return new HeroEventAdapter();
    }

    @Override
    public Set<String> tagsFor(HeroEvent event) {
        return AkkaTaggerAdapter.fromLagom(entityContext, HeroEvent.TAG).apply(event);
    }
}
