import java.io.IOException;
import com.amazonaws.util.Base64;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AmazonUtils {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final AWSSecretsManager awsSecretsClient =
            AWSSecretsManagerClientBuilder.standard().withRegion(System.getenv("AWS_REGION")).build();

    public static final Region awsRegion = Region.getRegion(Regions.fromName(System.getenv("AWS_REGION")));
    public static final Credentials cassandraCredentials = getCredentials("toh-lagom-cassandra");
    public static final Credentials postgresqlCredentials = getCredentials("toh-lagom-postgresql");

    private static Credentials getCredentials(String secretId) {
        assert awsSecretsClient != null;
        final GetSecretValueRequest request = new GetSecretValueRequest().withSecretId(secretId);
        final GetSecretValueResult result = awsSecretsClient.getSecretValue(request);

        final String plainText = result.getSecretString();
        final String secret = (null != plainText) ? plainText :
            new String(Base64.decode(result.getSecretBinary().array()));

        try {
            return mapper.readValue(secret, Credentials.class);
        } catch (final IOException e) {
            return null;
        }
    }
}
