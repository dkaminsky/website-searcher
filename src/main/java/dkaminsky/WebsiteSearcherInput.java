package dkaminsky;

import java.net.URL;
import java.util.regex.Pattern;

/**
 * Represents a single unit of work to be consumed by a single worker.
 */
public class WebsiteSearcherInput {
    private Pattern regexPattern;
    private URL url;

    WebsiteSearcherInput(Pattern regexPattern, URL url) {
        this.regexPattern = regexPattern;
        this.url = url;
    }

    /**
     * The compiled regular expression to evaluate against.
     * @return the regex pattern
     */
    public Pattern getRegexPattern() {
        return regexPattern;
    }

    /**
     * The URI to search for the pattern.
     * @return the URI to search
     */
    public URL getUrl() {
        return url;
    }
}
