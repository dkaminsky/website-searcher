package dkaminsky;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class WebsiteSearcherTests {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final String SITE_1 = "http://www.fakesite.com";
    private static final String SITE_2 = "http://www.somewhereelse.com";
    private static final String SITE_3 = "http://foobar.quux";
    private static final int NUM_SITES = 3;
    private static final String INPUT_STRING =
            new StringJoiner(LINE_SEPARATOR).add("HEADER").add("1," + SITE_1)
                    .add("2," + SITE_2).add("3," + SITE_3).toString();
    private static final long INPUT_READ_TIMEOUT_MILLIS = 10000L;

    private WebsiteSearcher underTest;
    private StringReader inputReader;
    private StringWriter outputWriter;
    private Pattern searchPattern;
    private BlockingQueue<WebsiteSearcherInput> inputQueue;
    private BlockingQueue<URL> outputQueue;

    @Before
    public void setUp() {
        inputReader = new StringReader(INPUT_STRING);
        outputWriter = new StringWriter();
        searchPattern = Pattern.compile("anything");
        inputQueue = new LinkedBlockingQueue<>();
        outputQueue = new LinkedBlockingQueue<>();

        underTest = new WebsiteSearcher(inputReader, outputWriter, searchPattern, inputQueue, outputQueue);
    }

    @After
    public void tearDown() {
        if (underTest.isRunning()) {
            underTest.shutdown();
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testWebsiteSearcherThrowsOnNullInputReader() {
        new WebsiteSearcher(null, outputWriter, searchPattern, inputQueue, outputQueue);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testWebsiteSearcherThrowsOnNullOutputWriter() {
        new WebsiteSearcher(inputReader, null, searchPattern, inputQueue, outputQueue);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testWebsiteSearcherThrowsOnNullSearchPattern() {
        new WebsiteSearcher(inputReader, outputWriter, null, inputQueue, outputQueue);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testWebsiteSearcherThrowsOnNullInputQueue() {
        new WebsiteSearcher(inputReader, outputWriter, searchPattern, null, outputQueue);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testWebsiteSearcherThrowsOnNullOutputQueue() {
        new WebsiteSearcher(inputReader, outputWriter, searchPattern, inputQueue, null);
    }

    @Test
    public void testEmptyInputFile() {
        StringReader emptyInputReader = new StringReader("");
        WebsiteSearcher searcher =
                new WebsiteSearcher(emptyInputReader, outputWriter, searchPattern, inputQueue, outputQueue);

        try {
            initSearcher(searcher, 0);
        } finally {
            searcher.shutdown();
        }
    }

    @Test
    public void testNonEmptyInputFile() {
        try {
            initSearcher(underTest, NUM_SITES);
        } finally {
            underTest.shutdown();
        }

        List<WebsiteSearcherInput> inputs = new ArrayList<>();
        inputQueue.drainTo(inputs);
        assertEquals(searchPattern, inputs.get(0).getRegexPattern());
        assertEquals(SITE_1, inputs.get(0).getUrl().toString());
        assertEquals(searchPattern, inputs.get(1).getRegexPattern());
        assertEquals(SITE_2, inputs.get(1).getUrl().toString());
        assertEquals(searchPattern, inputs.get(2).getRegexPattern());
        assertEquals(SITE_3, inputs.get(2).getUrl().toString());
    }

    @Test
    public void testWriteToOutput() throws MalformedURLException {
        try {
            initSearcher(underTest, NUM_SITES);
        } catch(Throwable t) {
            underTest.shutdown();
            throw t;
        }
        outputQueue.add(new URL(SITE_2));
        outputQueue.add(new URL(SITE_3));

        // in a production application we might instrument the WebsiteSearcher with a listener callback
        // to verify this is completed, but for our purposes now we will just wait a bit
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // no-op
        }
        underTest.shutdown(); // close the writer so it flushes

        assertEquals(new StringJoiner(LINE_SEPARATOR, "", LINE_SEPARATOR).add(SITE_2)
                        .add(SITE_3).toString(), outputWriter.getBuffer().toString());
    }

    private void initSearcher(WebsiteSearcher searcher, int numInputs) {
        searcher.start();

        try {
            searcher.getInputProcessed().await(INPUT_READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("Interrupted waiting for input");
        }

        assertEquals(numInputs, inputQueue.size());
        assertEquals(0, outputQueue.size());
    }
}
