# cyanite-remover changes

## 0.6.1

This is a bugfix release.

* Remove the `exclude-paths` options from commands `remove-empty-paths` and
`list-empty-paths` as useless and harmful

## 0.6.0

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

## 0.5.1

This is a minor feature and bugfix release.

* Add the `exclude-paths` option
* Enable the `--jobs` option for the `list-obsolete-data` command
* Don't delete non-leaf paths during the removal of obsolete data

## 0.5.0

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

## 0.4.0

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

## 0.3.1

This is a maintenance release.

* Delete metrics from branches as well

## 0.3.0

This is a minor feature and bugfix release.

* Add the `--help` command line option
* Add display duration time
* Fix path removing
* Add progress bar for path operations

## 0.2.0

This is a minor feature and bugfix release.

* Add the `--jobs` command line option
* Fix errors in logging
* Fix errors in stats

## 0.1.0

This is a first public release.
