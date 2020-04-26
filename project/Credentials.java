import lombok.Value;
import com.google.common.base.Preconditions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@Value
@JsonDeserialize
public final class Credentials {
    public final String username;
    public final String password;

    @JsonCreator
    public Credentials(@JsonProperty("username") String username,
                       @JsonProperty("password") String password) {
        this.username = Preconditions.checkNotNull(username, "username");
        this.password = Preconditions.checkNotNull(password, "password");
    }
}
