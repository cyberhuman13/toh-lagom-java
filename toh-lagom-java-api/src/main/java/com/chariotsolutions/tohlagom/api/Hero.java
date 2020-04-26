package com.chariotsolutions.tohlagom.api;

import lombok.Value;
import com.google.common.base.Preconditions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@Value
@JsonDeserialize
public final class Hero {
    public final String id;
    public final String name;

    @JsonCreator
    public Hero(@JsonProperty("id") String id,
                @JsonProperty("name") String name) {
        this.id = Preconditions.checkNotNull(id, "id");
        this.name = Preconditions.checkNotNull(name, "name");
    }
}
