# Website Searcher

## Summary
This naive implementation of a website searcher uses a *Producer/Consumer* pattern to search a list of websites
for a particular pattern. It is designed to be easily tested in an automated fashion by allowing the *reader*, *writer*,
and the *strategy for retrieving URLs* to be injected by the calling code via the constructor of the `WebsiteSearcher`
and `WebsiteSearcherWorker` classes. The `WebsiteSearcher` corresponds to a **searcher** thread which reads the input
from the given reader and adds it to a shared *input queue.* It then polls a shared *output queue* for results and is the
only thread that writes to the result output file. Each of the **worker** threads, as defined by the
`WebsiteSearcherWorker` class, consumes messages from the *input queue* one at a time. For each consumed URL/regex
pattern pair, it retrieves the content of the URL using its *URL streaming strategy* and executes the regular
expression match logic against the retrieved content. If any of the content matches, the URL is added to the *output
queue* for consumption by the **searcher** thread.

## Build
This project uses *gradle* - see <http://www.gradle.org> for more information.

* To clean: `./gradlew clean`
* To build: `./gradlew build`

## Running
This project is written using Java 8 and provides a Java 8 compatible jar file.

The main method of this implementation reads its input from the provided `urls.txt` file and produces a `results.txt`
file in the same directory.

To run (where *project_dir* is the root of the project as checked out from git):
```
cd project_dir/dist
java -jar website-searcher.jar
```
This assumes you have Java installed and available on your path.

The main method of this application searches the `urls.txt` file in the working
directory and for each URL, adds it to the `results.txt` if it contains the word
"and" anywhere in its content.

### Caveats
* Error handling is fairly minimal since the product specification does not give much detail on how errors ought to
be handled. Errors in the worker threads are printed to standard error and ignored (note that this may create rather
verbose output when a site is unreachable or its content unreadable). Other errors are generally bubbled up to the top.
This could be refined on future iterations of this product.
* This application does not terminate on its own. Since the searcher thread has no way of knowing how many
outputs it will receive (as the workers could return any number of matches from 0-N where N is the number of inputs)
or when it will receive them, it continues to block on the *output queue* waiting for more input. To terminate the
application, hit CTRL+C or send SIGKILL from a terminal and the application will clean up and exit.
