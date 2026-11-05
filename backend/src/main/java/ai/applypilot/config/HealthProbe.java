package ai.applypilot.config;

import java.net.HttpURLConnection;
import java.net.URI;

/** Tiny process probe used by the container healthcheck without adding curl/wget to the runtime image. */
public final class HealthProbe {
    private HealthProbe() {}
    public static void main(String[] args) throws Exception {
        var connection = (HttpURLConnection) URI.create("http://127.0.0.1:8080/api/health").toURL().openConnection();
        connection.setConnectTimeout(1500);
        connection.setReadTimeout(3500);
        connection.setRequestMethod("GET");
        if (connection.getResponseCode() != 200) System.exit(1);
        connection.disconnect();
    }
}
