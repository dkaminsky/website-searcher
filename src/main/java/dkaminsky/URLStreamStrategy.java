package dkaminsky;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Abstraction for creating an {@link InputStream} from a {@link URL}. Used for injecting
 * data for testing.
 */
public interface URLStreamStrategy {
    /**
     * Creates an input stream corresponding to the provided URL.
     * @param url The URL to open
     * @return A stream which accesses the data of the site corresponding to the provided URL
     */
    InputStream openStream(URL url) throws IOException;
}
