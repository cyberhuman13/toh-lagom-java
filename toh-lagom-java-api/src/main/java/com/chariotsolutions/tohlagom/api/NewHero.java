package com.chariotsolutions.tohlagom.api;

import lombok.Value;
import com.google.common.base.Preconditions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@Value
@JsonDeserialize
public final class NewHero {
    public final String name;

    @JsonCreator
    public NewHero(@JsonProperty("name") String name) {
        this.name = Preconditions.checkNotNull(name, "name");
    }
}
