# cyanite-remover changes

## Unreleased

* Replace `org.spootnik/logconfig` with `spootnik/unilog`
* Make code more Clojure-idiomatic
* clojure 1.8.0
* tools.cli 0.3.5
* alia 3.1.10
* alia-async 3.1.3
* snappy-java 1.1.2.6
* core.async 0.2.385
* elastisch 2.2.2
* claypoole 1.1.3
* clj-time 0.12.0
* math.combinatorics 0.1.3

[Diff](https://github.com/cybem/cyanite-remover/compare/0.6.2...HEAD)

## [0.6.2 - 2015-12-03](https://github.com/cybem/cyanite-remover/releases/tag/0.6.2)

This is a maintenance and bugfix release.

* Handle errors in the `check-metric-obsolete` function
* Use `com.climate.claypoole/with-shutdown!` for thread pools
* Add missing try-catch clauses to handle exceptions inside threads
* Cleanup code in `path_store.clj`
* core.async 0.2.374
* Update the documentation

[Diff](https://github.com/cybem/cyanite-remover/compare/0.6.1...0.6.2)

## [0.6.1 - 2015-10-28](https://github.com/cybem/cyanite-remover/releases/tag/0.6.1)

This is a bugfix release.

* Remove the `exclude-paths` options from commands `remove-empty-paths` and
`list-empty-paths` as useless and harmful

[Diff](https://github.com/cybem/cyanite-remover/compare/0.6.0...0.6.1)

## [0.6.0 - 2015-10-27](https://github.com/cybem/cyanite-remover/releases/tag/0.6.0)

This is a feature and maintenance release.

* Add the `remove-empty-paths` command
* Add the `list-empty-paths` command
* Add the `sort` option. From this moment output is unsorted by default.
* Use `delete` instead of `delete-by-query` to remove obsolete data
* Add the `elasticsearch-scroll-batch-size` option
* Add the `elasticsearch-scroll-batch-rate` option
* Add the `elasticsearch-delete-request-rate` option
* Remove the upper limit for the `cassandra-batch-rate` option
* Add the defaults `/etc/default/cyanite-remover` file to the Debian package
* Use set instead of hash-map for collecting paths information
* Use a common thread pool in the `remove-obsolete-data` command

[Diff](https://github.com/cybem/cyanite-remover/compare/0.5.1...0.6.0)

## [0.5.1 - 2015-10-20](https://github.com/cybem/cyanite-remover/releases/tag/0.5.1)

This is a minor feature and bugfix release.

* Add the `exclude-paths` option
* Enable the `--jobs` option for the `list-obsolete-data` command
* Don't delete non-leaf paths during the removal of obsolete data

[Diff](https://github.com/cybem/cyanite-remover/compare/0.5.0...0.5.1)

## [0.5.0 - 2015-10-13](https://github.com/cybem/cyanite-remover/releases/tag/0.5.0)

This is a feature and maintenance release.

* Add the `remove-obsolete-data` command
* Add the `list-obsolete-data` command
* Enable the `--jobs` option for the `remove-paths` command
* Reorder priority of search paths of the jar-file
* Use protocols in core
* Print errors to stderr instead of stdout
* Log command line arguments
* tools.cli 0.3.3
* claypoole 1.1.0
* clj-time 0.11.0
* snappy-java 1.1.2

[Diff](https://github.com/cybem/cyanite-remover/compare/0.4.0...0.5.0)

## [0.4.0 - 2015-06-14](https://github.com/cybem/cyanite-remover/releases/tag/0.4.0)

This is a minor feature, maintenance and bugfix release.

* Write the user's guide
* Use semicolons instead of commas in paths
* Use retention instead of period in CLI
* elastisch 2.2.0-beta4
* clj-progress 0.2.1
* claypoole 1.0.0
* snappy-java 1.1.1.7
* alia 2.5.3
* logconfig 0.7.3
* Clojure 1.7.0
* Add the license badge
* Add the Jarkeeper badge
* Support question mark in wildcards

[Diff](https://github.com/cybem/cyanite-remover/compare/0.3.1...0.4.0)

## [0.3.1 - 2015-06-04](https://github.com/cybem/cyanite-remover/releases/tag/0.3.1)

This is a maintenance release.

* Delete metrics from branches as well

[Diff](https://github.com/cybem/cyanite-remover/compare/0.3.0...0.3.1)

## [0.3.0 - 2015-05-31](https://github.com/cybem/cyanite-remover/releases/tag/0.3.0)

This is a minor feature and bugfix release.

* Add the `--help` command line option
* Add display duration time
* Fix path removing
* Add progress bar for path operations

[Diff](https://github.com/cybem/cyanite-remover/compare/0.2.0...0.3.0)

## [0.2.0 - 2015-05-14](https://github.com/cybem/cyanite-remover/releases/tag/0.2.0)

This is a minor feature and bugfix release.

* Add the `--jobs` command line option
* Fix errors in logging
* Fix errors in stats

[Diff](https://github.com/cybem/cyanite-remover/compare/0.1.0...0.2.0)

## [0.1.0 - 2015-04-29](https://github.com/cybem/cyanite-remover/releases/tag/0.1.2)

This is the first public release.
