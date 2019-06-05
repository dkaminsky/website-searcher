package dkaminsky;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * A worker thread that consumes an individual {@link URI} and a search {@link Pattern}
 * in the form of a {@link WebsiteSearcherInput} and possibly determines to add the
 * corresponding URL to the outputQueue if the pattern is detected in the retrieved data.
 *
 * Naively assumes {@link URL#openStream()} will function properly without doing any
 * special processing around HTTP headers, TCP options, proxy settings, etc.
 */
class WebsiteSearcherWorker extends Thread {
    private final BlockingQueue<WebsiteSearcherInput> inputQueue;
    private final BlockingQueue<URL> outputQueue;
    private final URLStreamStrategy urlStreamStrategy;
    private final AtomicBoolean running = new AtomicBoolean(true);

    WebsiteSearcherWorker(final BlockingQueue<WebsiteSearcherInput> inputQueue,
                          final BlockingQueue<URL> outputQueue,
                          final URLStreamStrategy urlStreamStrategy) {
        if (inputQueue == null) {
            throw new IllegalArgumentException("Null input queue passed to worker");
        }
        if (outputQueue == null) {
            throw new IllegalArgumentException("Null output queue passed to worker");
        }
        if (urlStreamStrategy == null) {
            throw new IllegalArgumentException("Null URL stream factory passed to worker");
        }

        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
        this.urlStreamStrategy = urlStreamStrategy;
    }

    /**
     * Indicates if the main processing loop of this thread will continue after the current iteration.
     *
     * @return Whether this thread will continue.
     */
    public boolean isRunning() {
        return this.running.get();
    }

    /**
     * Sets an internal flag telling the worker thread to stop running.
     */
    void shutdown() {
        this.running.set(false);
    }

    /**
     * The main work loop of the worker thread. Consumes a single item
     * from the inputQueue and reads it line by line, searching for the search pattern.
     *
     * If it finds a match, sends the URL of the matched data to the output queue.
     */
    @Override
    public void run() {
        while(running.get()) {
            try {
                final WebsiteSearcherInput input = inputQueue.take();

                final URL url = input.getUrl();
                final Predicate matcher = input.getRegexPattern().asPredicate();

                try (final InputStream in = urlStreamStrategy.openStream(url);
                     final InputStreamReader isReader = new InputStreamReader(in);
                     final BufferedReader reader = new BufferedReader(isReader)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (matcher.test(line)) {
                            outputQueue.offer(url);
                            break;
                        }
                    }
                } catch (IOException e) {
                    // nothing to do but print and continue
                    System.err.println("I/O exception reading data from URL: " + url.toString());
                    e.printStackTrace(System.err);
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
