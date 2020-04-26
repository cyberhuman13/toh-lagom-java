package com.chariotsolutions.tohlagom.impl;

import lombok.Value;
import com.google.common.base.Preconditions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.CompressedJsonable;

/**
 * The state for the {@link HeroAggregate} entity.
 */
@Value
@JsonDeserialize
public final class HeroState implements CompressedJsonable {
    public static final HeroState INITIAL = new HeroState("", false);
    public final String name;
    public final boolean valid;

    @JsonCreator
    HeroState(@JsonProperty("name") String name,
              @JsonProperty("valid") boolean valid) {
        this.name = Preconditions.checkNotNull(name, "name");
        this.valid = valid;
    }
}
