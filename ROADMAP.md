# batect roadmap

This file reflects my current plans. Something being listed here does not guarantee that I will implement it soon (or even ever),
and, similarly, just because something isn't here doesn't mean I won't ever implement it.

If there's something you're really keen to see, pull requests are always welcome :)

## MVP

### Config file handling
* better error message when a key (eg. a task name) is used twice (at the moment it's `Duplicate field 'duplicated_task_name'`)
* better error message when a dependency is given twice (at the moment it's `Duplicate value 'dependency-name''`)

### Features
* automatically enable `--no-color` or `--simple-output` if console doesn't support it (use terminfo database rather than current detection system)
* show more detailed image pull progress (eg. `build-env: Pulling some-image:1.2.3: 25%`) - requires using Docker API to get this level of detail
* performance improvements
  * prioritise running steps that lie on the critical path (eg. favour pulling image for leaf of dependency graph over creating container for task container)
  * print updates to the console asynchronously (they currently block whatever thread posts the event or is starting the step)
  * batch up printing updates to the console when using fancy output mode, rather than reprinting progress information on every event
* check that Docker client is available before trying to use it
* check that Docker client and server are compatible versions
* warn when using an image without a tag or with tag `latest`
* show a short summary after a task finishes (eg. `build finished with exit code X in 2.3 seconds`)
* support for Windows
* infer project name from project directory name if not provided

### Bugs
* use proxy settings when checking for updates
* fix the issue where if the fancy output mode is enabled and any of the lines of output is longer than the console width, the progress information
  doesn't correctly overwrite previous updates
  * handle the case where the console is resized while batect is running

### Other
* logging (for batect internals)
  * include process ID with each message (this is non-trivial in versions prior to Java 9: https://stackoverflow.com/questions/35842/how-can-a-java-program-get-its-own-process-id)
* option to print full stack trace on non-fatal exceptions
* for fatal exceptions (ie. crashes), add information on where to report the error (ie. GitHub issue)
* use Docker API directly rather than using Docker CLI (would allow for more detailed progress and error reporting)
* documentation
  * examples for common languages and scenarios
    * Golang
    * NodeJS
      * frontend
      * backend
    * Android app
  * importance of idempotency
  * improve the getting started guide (it's way too wordy)
* make error message formatting (eg. image build failed, container could not start) prettier and match other output (eg. use of bold for container names)
* make configuration-related error messages clearer and remove exception class names etc.
* easy way to update to new versions when notified (eg. `batect update` downloads new wrapper script and replaces it in place)
* use batect to build batect (self-hosting)
* move to Kotlin/Native
  * Why? Don't want to require users to install a JVM to use batect, also want to remove as much overhead as possible

### Things that would have to be changed when moving to Kotlin/Native
* would most likely need to replace YAML parsing code (although this would be a good opportunity to simplify it a
  bit and do more things while parsing the document rather than afterwards)
* file I/O and path resolution logic
* process creation / monitoring

### Things blocking move to Kotlin/Native
* unit testing support and associated library
* file I/O support
* process creation / monitoring support
* YAML parsing library
* [Kodein support](https://github.com/SalomonBrys/Kodein/tree/master/kodein-native)

## Future improvements
* warn if dependency exits before task finishes (include exit code)
* running multiple containers at once (eg. stereotypical 'run' configuration that starts up the service with its dependencies)
  * exit options (close all after any container stops, wait for all to stop)
  * rather than showing output from target, show output from all containers
  * logging options (all or particular container)
  * return code options (any non-zero, particular container, first to exit)
* allow configuration includes (ie. allow splitting the configuration over multiple files)
* wildcard includes (eg. `include: containers/*.yaml`)
* handle expanded form of mappings, for example:

  ```yaml
  containers:
    build-env:
      build_dir: build-env
      environment:
        - name: THING
          value: thing_value

  ```

* support port ranges in mappings
* support protocols other than TCP in port mappings
* shell tab completion for options (eg. `batect --h<tab>` completes to `batect --help`)
* shell tab completion for tasks (eg. `batect b<tab>` completes to `batect build`)
* requires / provides relationships (eg. 'app' requires 'service-a', and 'service-a-fake' and 'service-a-real' provide 'service-a')
* don't do all path resolution up-front
  * if not all containers are used, doesn't make sense to try to resolve their paths
  * would save some time
  * means user doesn't see irrelevant error messages
* when starting up containers and displaying progress, show countdown to health check (eg. 'waiting for container to become healthy, next check in 3 seconds')
* warn if a dependency does not have a health check defined
* default to just terminating all containers at clean up time with option to gracefully shut down on individual containers
  (eg. database where data is shared between invocations and we don't want to corrupt it)
* fancy progress bar output for building images and starting dependencies
  * make sure accidental input on stdin doesn't mangle it
  * test with different console colour schemes (eg. white background, black background, OS X default, Ubuntu default, Ubuntu GUI terminal default)
* some way to group tasks shown when running `batect --list-tasks`
* group display of options shown when running `batect --help`
