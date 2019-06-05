package dkaminsky;

import java.io.*;
import java.net.URL;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class WebsiteSearcher extends Thread {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final int MAX_THREADS = 20;
    private static final String DEFAULT_SEARCH_EXPRESSION = ".*\\sand\\s.*";
    private static final Pattern DEFAULT_SEARCH_PATTERN =
            Pattern.compile(DEFAULT_SEARCH_EXPRESSION, Pattern.CASE_INSENSITIVE);
    private static final String DEFAULT_INPUT_FILE_PATH = "urls.txt";
    private static final String DEFAULT_OUTPUT_FILE_PATH = "results.txt";
    private static final String HTTP_SCHEME = "http://";

    private final Reader input;
    private final Writer output;

    private final Pattern searchExpressionPattern;
    private final BlockingQueue<WebsiteSearcherInput> inputQueue;
    private final BlockingQueue<URL> outputQueue;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private CountDownLatch inputProcessed = new CountDownLatch(1);

    /**
     * Represents a thread that creates the input for and coordinates the output from all of the search worker
     * threads. Uses an input and output queue to mediate communication work worker threads. The input and output
     * {@link Reader}/{@link Writer} pair are exposed as constructor arguments to facilitate testing. The caller is
     * responsible for their proper initialization and destruction.
     *
     * @param input An open reader whose data represents the set of URLs to check
     * @param output An open writer where matching sites will be written.
     * @param searchExpressionPattern The pattern to tell each worker to search for.
     * @param inputQueue The queue from which workers will receive inputs created by this thread
     * @param outputQueue The queue to which workers will send results to be written by this thread
     */
    public WebsiteSearcher(final Reader input, final Writer output, final Pattern searchExpressionPattern,
                           final BlockingQueue<WebsiteSearcherInput> inputQueue, final BlockingQueue<URL> outputQueue) {
        super("WebSearcher");
        if (input == null) {
            throw new IllegalArgumentException("Input reader is null");
        }
        if (output == null) {
            throw new IllegalArgumentException("Output writer is null");
        }
        if (searchExpressionPattern == null) {
            throw new IllegalArgumentException("Search pattern is null");
        }
        if (inputQueue == null) {
            throw new IllegalArgumentException("Input queue is null");
        }
        if (outputQueue == null) {
            throw new IllegalArgumentException("Output queue is null");
        }

        this.input = input;
        this.output = output;
        this.searchExpressionPattern = searchExpressionPattern;
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
    }

    /**
     * Starts the website searcher thread.
     */
    @Override
    public void run() {
        // read each line from the input file, construct input for the workers and enquuee it
        try(final BufferedReader br = new BufferedReader(input)) {
            String line;
            br.readLine(); // discard the first line, which is a header
            while ((line = br.readLine()) != null) {
                String[] csvLine = line.split(",");
                if (csvLine.length < 2) {
                    continue;
                }
                // grab second column
                String urlStr = csvLine[1].trim();
                // strip quotes
                if (urlStr.matches("\\\"[^\\\"]+\\\"")) {
                    urlStr = urlStr.substring(1, urlStr.length()-1);
                }
                // add scheme
                if (!urlStr.startsWith(HTTP_SCHEME)){
                    urlStr = HTTP_SCHEME + urlStr;
                }
                // construct URL and create input
                final URL url = new URL(urlStr);
                final WebsiteSearcherInput input = new WebsiteSearcherInput(searchExpressionPattern, url);
                inputQueue.offer(input);
            }
        } catch (IOException e) {
            // nothing to really do except toss it upward
            throw new RuntimeException("Error reading from input", e);
        }

        inputProcessed.countDown();

        // consume input, dispatching each new URL to a different worker
        while (running.get()) {
            final URL matchingURL;
            try {
                matchingURL = outputQueue.take();
                output.append(matchingURL.toString());
                output.append(LINE_SEPARATOR);
                output.flush(); // in a production scenario we may flush less often, particularly if
                                // we are writing to a "slow" device such as a traditional magnetic disk
            } catch (IOException e) {
                // nothing to really do except toss it upward
                throw new RuntimeException("Failed to write to output", e);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Indicates if the output processing loop is set to continue running. Exposed for testing.
     *
     * @return Whether or not the output processing loop is set to continue running.
     */
    public boolean isRunning() {
        return this.running.get();
    }

    /**
     * Tells this searcher that the output processing loop should stop running and allow the thread to terminate
     * naturally.
     */
    void shutdown() {
        this.running.set(false);
    }

    /**
     * Returns a latch whose condition indicates whether or not all input has been
     * read from the list of URLs. Exposed for testing.
     *
     * @return The latch
     */
    CountDownLatch getInputProcessed() {
        return inputProcessed;
    }

    public static void main(final String[] args) throws InterruptedException {
        File inputFile = new File(DEFAULT_INPUT_FILE_PATH);
        File outputFile = new File(DEFAULT_OUTPUT_FILE_PATH);

        // create a queue to be shared by workers
        // use linked instead of array because we don't know in advance the data size
        BlockingQueue<WebsiteSearcherInput> inputQueue = new LinkedBlockingQueue<>();
        BlockingQueue<URL> outputQueue = new LinkedBlockingQueue<>();

        // if input file doesn't exist or isn't readable, fail fast
        if (!inputFile.isFile() || !inputFile.canRead()) {
            throw new IllegalArgumentException("File does not exist or is unreadable: " + inputFile.getAbsolutePath());
        }

        // if output file exists, delete it if it's a normal file, fail fast if we can't delete it,
        // fail fast if it's not a normal file
        if (outputFile.exists()) {
            if (!outputFile.isFile()) {
                throw new IllegalArgumentException("Output file exists and is not a regular file: "
                        + outputFile.getAbsolutePath());
            }
            boolean deleted = outputFile.delete();
            if (!deleted) {
                throw new IllegalArgumentException("Output file exists and could not be deleted: "
                        + outputFile.getAbsolutePath());
            }
        }

        // for main method, use URL.openStream method naively. Abstracted for testing.
        final URLStreamStrategy urlStreamFactory = new OpenStreamURLStreamStrategy();

        final WebsiteSearcherWorker[] workers = new WebsiteSearcherWorker[MAX_THREADS];
        final WebsiteSearcher searcher;

        for (int i = 0; i < MAX_THREADS; i++) {
            final WebsiteSearcherWorker workerThread =
                new WebsiteSearcherWorker(inputQueue, outputQueue, urlStreamFactory);

            workerThread.start();
            workers[i] = workerThread;
        }

        final FileReader inReader;
        final FileWriter outWriter;
        try {
            inReader = new FileReader(inputFile);
            outWriter = new FileWriter(outputFile);
        } catch (IOException e) {
            throw new IllegalStateException("I/O error reading from input or writing to output");
        }
        searcher = new WebsiteSearcher(inReader, outWriter, DEFAULT_SEARCH_PATTERN, inputQueue, outputQueue);

        // Make sure all threads finish whenever the program exits, even
        // if it's on a SIGKILL from the OS
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            searcher.shutdown();
            for (WebsiteSearcherWorker workerThread : workers) {
                if (workerThread != null) {
                    workerThread.shutdown();
                }
            }
        }));

        try {
            searcher.start();
            searcher.join();
        } finally {
            try {
                inReader.close();
                outWriter.close();
            } catch(IOException e) {
                // nothing to do
            }
        }
    }
}
