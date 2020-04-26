package com.chariotsolutions.tohlagom.impl;

import lombok.Value;
import akka.actor.typed.ActorRef;
import com.google.common.base.Preconditions;
import com.lightbend.lagom.serialization.Jsonable;
import com.lightbend.lagom.serialization.CompressedJsonable;

/**
  * This interface defines all the commands that the Hero aggregate supports.
 * <p>
 * By convention, the commands and replies should be inner classes of the
 * interface, which makes it simple to get a complete picture of what commands
 * an aggregate supports.
 */
public interface HeroCommand extends Jsonable {
    /**
     * A command to create a hero.
     * It has a reply type of [[Confirmation]], which is sent back to the caller
     * when all the events emitted by this command are successfully persisted.
     */
    @Value
    final class CreateHero implements HeroCommand, CompressedJsonable {
        public final String name;
        public final ActorRef<Confirmation> replyTo;

        public CreateHero(String name, ActorRef<Confirmation> replyTo) {
            this.name = Preconditions.checkNotNull(name, "name");
            this.replyTo = replyTo;
        }
    }

    /**
     * A command to change a hero.
     * It has a reply type of [[Confirmation]], which is sent back to the caller
     * when all the events emitted by this command are successfully persisted.
     */
    @Value
    final class ChangeHero implements HeroCommand, CompressedJsonable {
        public final String newName;
        public final ActorRef<Confirmation> replyTo;

        public ChangeHero(String newName, ActorRef<Confirmation> replyTo) {
            this.newName = Preconditions.checkNotNull(newName, "newName");
            this.replyTo = replyTo;
        }
    }

    /**
     * A command to delete a hero.
     * It has a reply type of [[Confirmation]], which is sent back to the caller
     * when all the events emitted by this command are successfully persisted.
     */
    @Value
    final class DeleteHero implements HeroCommand, CompressedJsonable {
         public final ActorRef<Confirmation> replyTo;

         public DeleteHero(ActorRef<Confirmation> replyTo) {
            this.replyTo = replyTo;
        }
    }

    /**
     * A command to fetch a hero (for unit tests only).
     * It has a reply type of [[HeroState]], which is sent back to the caller
     * when all the events emitted by this command are successfully persisted.
     */
    @Value
    final class FetchHero implements HeroCommand, CompressedJsonable {
        public final ActorRef<HeroState> replyTo;

        public FetchHero(ActorRef<HeroState> replyTo) {
            this.replyTo = replyTo;
        }
    }
}
