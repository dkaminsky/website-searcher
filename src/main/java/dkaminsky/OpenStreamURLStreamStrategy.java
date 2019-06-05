package dkaminsky;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Naive implementation of stream factory that uses the {@link URL#openConnection()} method to open a stream
 * to the specified URL.
 */
public class OpenStreamURLStreamStrategy implements URLStreamStrategy {
    @Override
    public InputStream openStream(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.addRequestProperty("User-Agent", "Mozilla/5.0"); // spoof a well-known agent to avoid 403 errors

        return connection.getInputStream();
    }
}
