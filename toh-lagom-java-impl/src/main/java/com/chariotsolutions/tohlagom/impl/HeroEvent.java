package com.chariotsolutions.tohlagom.impl;

import lombok.Value;
import com.google.common.base.Preconditions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.serialization.Jsonable;

/**
 * This interface defines all the events that HeroAggregate emits.
 * <p>
 * By convention, the events should be inner classes of the interface, which
 * makes it simple to get a complete picture of what events an entity has.
 */
public interface HeroEvent extends Jsonable, AggregateEvent<HeroEvent> {
    /**
     * Tags are used for getting and publishing streams of events. Each event
     * will have this tag, and in this case, we are partitioning the tags into
     * 2 shards, which means we can have 2 concurrent processors/publishers of
     * events.
     */
    int NUM_SHARDS = 2;
    AggregateEventTagger<HeroEvent> TAG = NUM_SHARDS > 1
            ? AggregateEventTag.sharded(HeroEvent.class, NUM_SHARDS)
            : AggregateEventTag.of(HeroEvent.class);

    @Override
    default AggregateEventTagger<HeroEvent> aggregateTag() {
        return TAG;
    }

    @Value
    @JsonDeserialize
    final class HeroCreated implements HeroEvent {
        public final String id;
        public final String name;

        @JsonCreator
        HeroCreated(@JsonProperty("id") String id,
                    @JsonProperty("name") String name) {
            this.id = Preconditions.checkNotNull(id, "id");
            this.name = Preconditions.checkNotNull(name, "name");
        }
    }

    @Value
    @JsonDeserialize
    final class HeroChanged implements HeroEvent {
        public final String id;
        public final String newName;
        public final String oldName;

        @JsonCreator
        HeroChanged(@JsonProperty("id") String id,
                    @JsonProperty("newName") String newName,
                    @JsonProperty("oldName") String oldName) {
            this.id = Preconditions.checkNotNull(id, "id");
            this.newName = Preconditions.checkNotNull(newName, "newName");
            this.oldName = Preconditions.checkNotNull(oldName, "oldName");
        }
    }

    @Value
    @JsonDeserialize
    final class HeroDeleted implements HeroEvent {
        public final String id;

        @JsonCreator
        HeroDeleted(@JsonProperty("id") String id) {
            this.id = Preconditions.checkNotNull(id, "id");
        }
    }
}
