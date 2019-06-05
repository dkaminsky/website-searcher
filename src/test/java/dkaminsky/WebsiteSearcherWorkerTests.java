package dkaminsky;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class WebsiteSearcherWorkerTests {
    private static final String SITE_1 = "http://www.fakesite.com";
    private static final String SITE_2 = "http://www.somewhereelse.com";
    private static final String SITE_3 = "http://foobar.quux";
    private static final Map<String, String> websites = new HashMap<>();

    private WebsiteSearcherWorker underTest;
    private Pattern searchPattern;
    private BlockingQueue<WebsiteSearcherInput> inputQueue;
    private BlockingQueue<URL> outputQueue;

    static {
        websites.put(SITE_1, "hello world\nI am a website\n");
        websites.put(SITE_2, "a\nb\nc\nd\ne\nf\nz");
        websites.put(SITE_3, "this is a website with lots of bizarre text");
    }

    @Before
    public void setUp() {
        searchPattern = Pattern.compile("z+", Pattern.CASE_INSENSITIVE);
        inputQueue = new LinkedBlockingQueue<>();
        outputQueue = new LinkedBlockingQueue<>();
    }

    @After
    public void tearDown() {
        if (underTest != null && underTest.isRunning()) {
            underTest.shutdown();
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testWebsiteSearcherWorkerThrowsExceptionOnNullInputQueue() {
        new WebsiteSearcherWorker(null, outputQueue, new EmptyURLStreamStrategy());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testWebsiteSearcherWorkerThrowsExceptionOnNullOutputQueue() {
        new WebsiteSearcherWorker(inputQueue, null, new EmptyURLStreamStrategy());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testWebsiteSearcherWorkerThrowsExceptionOnNullURLStreamStrategy() {
        new WebsiteSearcherWorker(inputQueue, outputQueue, null);
    }

    @Test
    public void testExceptionInputs() {
        underTest = new WebsiteSearcherWorker(inputQueue, outputQueue, new ExceptionThrowingURLStreamStrategy());

        addSite(SITE_2);

        // for a production system we would instrument with some kind of listener but for now just wait a bit
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // no-op
        }

        underTest.shutdown();

        assertEquals(0, outputQueue.size());
    }

    @Test
    public void testEmptyInput() {
        underTest = new WebsiteSearcherWorker(inputQueue, outputQueue, new EmptyURLStreamStrategy());

        underTest.start();

        addSite(SITE_1);
        addSite(SITE_2);
        addSite(SITE_3);

        // for a production system we would instrument with some kind of listener but for now just wait a bit
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // no-op
        }

        underTest.shutdown();

        assertEquals(0, outputQueue.size());
    }

    @Test
    public void testNonMatchingInputs() {
        underTest = new WebsiteSearcherWorker(inputQueue, outputQueue, new StringReaderURLStreamStrategy());

        underTest.start();

        addSite(SITE_1);

        // for a production system we would instrument with some kind of listener but for now just wait a bit
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // no-op
        }

        underTest.shutdown();

        assertEquals(0, outputQueue.size());
    }

    @Test
    public void testAllMatchingInputs() {
        underTest = new WebsiteSearcherWorker(inputQueue, outputQueue, new StringReaderURLStreamStrategy());

        underTest.start();

        addSite(SITE_2);
        addSite(SITE_3);

        // for a production system we would instrument with some kind of listener but for now just wait a bit
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // no-op
        }

        underTest.shutdown();

        assertEquals(2, outputQueue.size());
        try {
            assertEquals(SITE_2, outputQueue.take().toString());
            assertEquals(SITE_3, outputQueue.take().toString());
        } catch (InterruptedException e) {
            // no-op
        }
    }

    @Test
    public void testMixedInputs() {
        underTest = new WebsiteSearcherWorker(inputQueue, outputQueue, new StringReaderURLStreamStrategy());

        underTest.start();

        addSite(SITE_1);
        addSite(SITE_2);
        addSite(SITE_3);

        // for a production system we would instrument with some kind of listener but for now just wait a bit
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // no-op
        }

        underTest.shutdown();

        assertEquals(2, outputQueue.size());
        try {
            assertEquals(SITE_2, outputQueue.take().toString());
            assertEquals(SITE_3, outputQueue.take().toString());
        } catch (InterruptedException e) {
            // no-op
        }
    }

    private void addSite(String site) {
        try {
            inputQueue.offer(new WebsiteSearcherInput(searchPattern, new URL(site)));
        } catch (MalformedURLException e) {
            throw new IllegalStateException("malformed constant in test: " + site);
        }
    }

    static class StringReaderURLStreamStrategy implements URLStreamStrategy {
        @Override
        public InputStream openStream(URL url) {
            String text = websites.get(url.toString());
            return new ByteArrayInputStream(text == null ? new byte[0] : text.getBytes());
        }
    }

    static class EmptyURLStreamStrategy implements URLStreamStrategy {
        @Override
        public InputStream openStream(URL url) {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    static class ExceptionThrowingURLStreamStrategy implements URLStreamStrategy {
        @Override
        public InputStream openStream(URL url) throws IOException {
            throw new IOException("");
        }
    }
}
