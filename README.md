# cyanite-remover

[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://raw.githubusercontent.com/cybem/cyanite-remover/master/LICENSE)
[![Build Status](https://travis-ci.org/cybem/cyanite-remover.svg?branch=master)](https://travis-ci.org/cybem/cyanite-remover)
[![Dependencies Status](http://jarkeeper.com/cybem/cyanite-remover/status.svg)](http://jarkeeper.com/cybem/cyanite-remover)

cyanite-remover is a [Cyanite](https://github.com/pyr/cyanite) data removal
tool.

## Table of Contents

* [Building](#building)
  * [Dependencies](#dependencies)
  * [Building a Standalone JAR-file](#building-a-standalone-jar-file)
  * [Building a Deb-package](#building-a-deb-package)
* [Usage](#usage)
  * [Quick Help](#quick-help)
  * [Commands](#commands)
  * [Arguments](#arguments)
  * [Options](#options)
* [Usage Scenarios](#usage-scenarios)
  * [Inspecting](#inspecting)
    * [Listing Metrics from Cassandra](#listing-metrics-from-cassandra)
    * [Listing Paths from Elasticsearch](#listing-paths-from-elasticsearch)
  * [Removing](#removing)
    * [Removing Metrics from Cassandra](#removing-metrics-from-cassandra)
    * [Removing Paths from Elasticsearch](#removing-paths-from-elasticsearch)
* [License](#license)
* [Thanks](#thanks)

## Building

### Dependencies

cyanite-remover is a [Clojure](http://clojure.org/) application and uses
[Leiningen](http://leiningen.org/) as build tool. Building cyanite-remover
needs a working Leiningen installation, as well as JDK.

### Building a Standalone JAR-file

```bash
lein uberjar
```

Built JAR-file will be placed in the `target/uberjar` directory. You can launch
the tool by running `./cyanite-remover` command.

### Building a Deb-package

Building cyanite-remover deb-package needs installed `dpkg-dev` and `fakeroot`
packages.

```bash
lein fatdeb
```

Built package will be placed in the `target` directory.

## Usage

### Quick Help

    cyanite-remover [options] remove-metrics <tenant> <rollup,...> <path,...> <cassandra_host,...> <elasticsearch_url>
    cyanite-remover [options] remove-paths <tenant> <path,...> <elasticsearch_url>
    cyanite-remover [options] remove-obsolete-data <tenant> <rollup,...> <path,...> <cassandra_host,...> <elasticsearch_url>
    cyanite-remover [options] remove-empty-paths <tenant> <path,...> <elasticsearch_url>
    cyanite-remover [options] list-metrics <tenant> <rollup,...> <path,...> <cassandra_host,...> <elasticsearch_url>
    cyanite-remover [options] list-paths <tenant> <path,...> <elasticsearch_url>
    cyanite-remover [options] list-obsolete-data <tenant> <rollup,...> <path,...> <cassandra_host,...> <elasticsearch_url>
    cyanite-remover [options] list-empty-paths <tenant> <path,...> <elasticsearch_url>
    cyanite-remover help

See [commands](#commands), [arguments](#arguments) and [options](#options) for
more details.

### Commands

* [remove-metrics](#remove-metrics)
* [remove-paths](#remove-paths)
* [remove-obsolete-data](#remove-obsolete-data)
* [remove-empty-paths](#remove-empty-paths)
* [list-metrics](#list-metrics)
* [list-paths](#list-paths)
* [list-obsolete-data](#list-obsolete-data)
* [list-empty-paths](#list-empty-paths)
* [help](#help)

#### `remove-metrics`

Remove metrics from Cassandra.

*`cyanite-remover` `remove-metrics`* [[*`options`*](#options)]
[*`tenant`*](#tenant) [*`rollup(s)`*](#rollups) [*`path(s)`*](#paths)
[*`cassandra_host(s)`*](#cassandra_hosts)
[*`elasticsearch_url`*](#elasticsearch_url)

Available options: [`cassandra-batch-rate`](#cassandra-batch-rate),
[`cassandra-batch-size`](#cassandra-batch-size),
[`cassandra-channel-size`](#cassandra-channel-size),
[`cassandra-keyspace`](#cassandra-keyspace),
[`cassandra-options`](#cassandra-options), [`disable-log`](#disable-log),
[`disable-progress`](#disable-progress),
[`elasticsearch-index`](#elasticsearch-index),
[`elasticsearch-scroll-batch-rate`](#elasticsearch-scroll-batch-rate),
[`elasticsearch-scroll-batch-size`](#elasticsearch-scroll-batch-size),
[`exclude-paths`](#exclude-paths), [`from`](#from), [`jobs`](#jobs),
[`log-file`](#log-file), [`log-level`](#log-level), [`run`](#run),
[`sort`](#sort), [`stop-on-error`](#stop-on-error), [`to`](#to).

See example of usage [here](#removing-metrics-from-cassandra).

**Before removing data, [make sure](#listing-metrics-from-cassandra) that you
are going to remove the desired data!**

#### `remove-paths`

Remove paths from Elasticsearch.

*`cyanite-remover` `remove-paths`* [[*`options`*](#options)]
[*`tenant`*](#tenant) [*`path(s)`*](#paths)
[*`elasticsearch_url`*](#elasticsearch_url)

Available options: [`disable-log`](#disable-log),
[`disable-progress`](#disable-progress),
[`elasticsearch-index`](#elasticsearch-index),
[`elasticsearch-scroll-batch-rate`](#elasticsearch-scroll-batch-rate),
[`elasticsearch-scroll-batch-size`](#elasticsearch-scroll-batch-size),
[`exclude-paths`](#exclude-paths), [`log-file`](#log-file),
[`log-level`](#log-level), [`run`](#run), [`sort`](#sort),.

See example of usage [here](#removing-paths-from-elasticsearch).

**Before removing data, [make sure](#listing-paths-from-elasticsearch) that you
are going to remove the desired data!**

**Always remove metrics first. Deletion of paths will make it impossible to
remove relevant metrics!**

#### `remove-obsolete-data`

Remove obsolete data from Cassandra and Elasticsearch.

*`cyanite-remover` `remove-obsolete-data`* [[*`options`*](#options)]
[*`tenant`*](#tenant) [*`rollup(s)`*](#rollups) [*`path(s)`*](#paths)
[*`cassandra_host(s)`*](#cassandra_hosts)
[*`elasticsearch_url`*](#elasticsearch_url)

Obsolete data is metrics that has not been updated in a while and its
paths.

By default, a metric is considered obsolete if it has not been updated for
2678400 seconds (31 day).

The threshold of the obsolescence can be adjusted using the
[`threshold`](#threshold) option.

Available options: [`cassandra-batch-rate`](#cassandra-batch-rate),
[`cassandra-batch-size`](#cassandra-batch-size),
[`cassandra-channel-size`](#cassandra-channel-size),
[`cassandra-keyspace`](#cassandra-keyspace),
[`cassandra-options`](#cassandra-options), [`disable-log`](#disable-log),
[`disable-progress`](#disable-progress),
[`elasticsearch-delete-request-rate`](#elasticsearch-delete-request-rate),
[`elasticsearch-index`](#elasticsearch-index),
[`elasticsearch-scroll-batch-rate`](#elasticsearch-scroll-batch-rate),
[`elasticsearch-scroll-batch-size`](#elasticsearch-scroll-batch-size),
[`exclude-paths`](#exclude-paths), [`jobs`](#jobs), [`log-file`](#log-file),
[`log-level`](#log-level), [`run`](#run), [`sort`](#sort),
[`stop-on-error`](#stop-on-error), [`threshold`](#threshold).

#### `remove-empty-paths`

Remove empty paths.

*`cyanite-remover` `remove-empty-paths`* [[*`options`*](#options)]
[*`tenant`*](#tenant) [*`path(s)`*](#paths)
[*`elasticsearch_url`*](#elasticsearch_url)

An empty path is a non-leaf path that has no children.

Available options: [`disable-log`](#disable-log),
[`disable-progress`](#disable-progress),
[`elasticsearch-delete-request-rate`](#elasticsearch-delete-request-rate),
[`elasticsearch-index`](#elasticsearch-index),
[`elasticsearch-scroll-batch-rate`](#elasticsearch-scroll-batch-rate),
[`elasticsearch-scroll-batch-size`](#elasticsearch-scroll-batch-size),
[`jobs`](#jobs), [`log-file`](#log-file), [`log-level`](#log-level),
[`run`](#run), [`sort`](#sort), [`stop-on-error`](#stop-on-error).

#### `list-metrics`

List metrics from Cassandra.

*`cyanite-remover` `list-metrics`* [[*`options`*](#options)]
[*`tenant`*](#tenant) [*`rollup(s)`*](#rollups) [*`path(s)`*](#paths)
[*`cassandra_host(s)`*](#cassandra_hosts)
[*`elasticsearch_url`*](#elasticsearch_url)

Available options: [`cassandra-keyspace`](#cassandra-keyspace),
[`cassandra-options`](#cassandra-options),
[`elasticsearch-index`](#elasticsearch-index),
[`elasticsearch-scroll-batch-rate`](#elasticsearch-scroll-batch-rate),
[`elasticsearch-scroll-batch-size`](#elasticsearch-scroll-batch-size),
[`exclude-paths`](#exclude-paths), [`from`](#from), [`sort`](#sort),
[`to`](#to).

See example of usage [here](#listing-metrics-from-cassandra).

#### `list-paths`

List paths from Elasticsearch.

*`cyanite-remover` `list-paths`* [[*`options`*](#options)]
[*`tenant`*](#tenant) [*`path(s)`*](#paths)
[*`elasticsearch_url`*](#elasticsearch_url)

Available options: [`elasticsearch-index`](#elasticsearch-index),
[`elasticsearch-scroll-batch-rate`](#elasticsearch-scroll-batch-rate),
[`elasticsearch-scroll-batch-size`](#elasticsearch-scroll-batch-size),
[`exclude-paths`](#exclude-paths), [`sort`](#sort).

See example of usage [here](#listing-paths-from-elasticsearch).

#### `list-obsolete-data`

List obsolete data.

*`cyanite-remover` `remove-obsolete-data`* [[*`options`*](#options)]
[*`tenant`*](#tenant) [*`rollup(s)`*](#rollups) [*`path(s)`*](#paths)
[*`cassandra_host(s)`*](#cassandra_hosts)
[*`elasticsearch_url`*](#elasticsearch_url)

See command [`remove-obsolete-data`](#remove-obsolete-data) for more details.

Available options: [`cassandra-keyspace`](#cassandra-keyspace),
[`cassandra-options`](#cassandra-options),
[`elasticsearch-index`](#elasticsearch-index),
[`elasticsearch-scroll-batch-rate`](#elasticsearch-scroll-batch-rate),
[`elasticsearch-scroll-batch-size`](#elasticsearch-scroll-batch-size),
[`exclude-paths`](#exclude-paths), [`jobs`](#jobs), [`sort`](#sort),
[`threshold`](#threshold).

#### `list-empty-paths`

List empty paths.

*`cyanite-remover` `list-empty-paths`* [[*`options`*](#options)]
[*`tenant`*](#tenant) [*`path(s)`*](#paths)
[*`elasticsearch_url`*](#elasticsearch_url)

See command [`remove-empty-paths`](#remove-empty-paths) for more details.

Available options: [`elasticsearch-index`](#elasticsearch-index),
[`elasticsearch-scroll-batch-rate`](#elasticsearch-scroll-batch-rate),
[`elasticsearch-scroll-batch-size`](#elasticsearch-scroll-batch-size),
[`jobs`](#jobs), [`sort`](#sort).

#### `help`

*`cyanite-remover` `help`*

Show help.

### Arguments

* [tenant](#tenant)
* [rollup(s)](#rollups)
* [path(s)](#paths)
* [cassandra_host(s)](#cassandra_hosts)
* [elasticsearch_url](#elasticsearch_url)

#### `tenant`

A tenant name.

#### `rollup(s)`

A comma-separated list of rollups.

Format: `<seconds_per_point:retention,...>`

Example: `60:5356800,900:62208000`

#### `path(s)`

A semicolon-separated list of paths.

Accepted wildcards are:
* An asterisk `*`. Matches any number of characters. Example:
  `requests.nginx.*`
* A question mark `?`. Matches a single character only. Example:
  `node1.cpu.?`
* A list `{path1,path2,...}`. Matches any string in a list. Example:
  `{nginx,apache}.cpu.0`
* A range `[M-N]`. Matches any number in the range from `M` to `N`. Example:
  `node[3-17].cpu.0`

Example: `"requests.nginx.*;node[3-17].cpu.?"`

#### `cassandra_host(s)`

A comma-separated list of Cassandra hosts.

Example: `cass1.example.org,cass2.example.org`

#### `elasticsearch_url`

An Elasticsearch REST service URL.

Example: `http://es.example.org:9200`

### Options

Options in alphabet order:

* [cassandra-batch-rate](#cassandra-batch-rate)
* [cassandra-batch-size](#cassandra-batch-size)
* [cassandra-channel-size](#cassandra-channel-size)
* [cassandra-keyspace](#cassandra-keyspace)
* [cassandra-options](#cassandra-options)
* [disable-progress](#disable-progress)
* [elasticsearch-index](#elasticsearch-index)
* [elasticsearch-delete-request-rate](#elasticsearch-delete-request-rate)
* [elasticsearch-scroll-batch-rate](#elasticsearch-scroll-batch-rate)
* [elasticsearch-scroll-batch-size](#elasticsearch-scroll-batch-size)
* [exclude-paths](#exclude-paths)
* [from](#from)
* [help](#help)
* [jobs](#jobs)
* [log-file](#log-file)
* [log-level](#log-level)
* [run](#run)
* [sort](#sort)
* [stop-on-error](#stop-on-error)
* [to](#to)
* [threshold](#threshold)

#### `cassandra-batch-rate`

`--cassandra-batch-rate` `RATE`

Set the Cassandra batch rate (batches per second).

Throttling is not used by default.

#### `cassandra-batch-size`

`--cassandra-batch-size` `SIZE`

Set the Cassandra batch size.

Default: `1000`

#### `cassandra-channel-size`

`--cassandra-channel-size` `SIZE`

Set the Cassandra channel size.

Default: `10000`

#### `cassandra-keyspace`

`--cassandra-keyspace` `KEYSPACE`

Set the Cassandra keyspace.

Default: `metric`

#### `cassandra-options`

`-O`, `--cassandra-options` `OPTIONS`

Set Cassandra options. See
[Alia documentation](https://mpenet.github.io/alia/qbits.alia.html#var-cluster)
for more details.

Example: `"{:compression :lz4}"`

#### `disable-progress`

`-P`, `--disable-progress`

Disable the progress bar.

#### `elasticsearch-index`

`--elasticsearch-index` `INDEX`

Set the Elasticsearch index.

Default: `cyanite_paths`

#### `elasticsearch-delete-request-rate`

`--elasticsearch-delete-request-rate` `RATE`

Set the Elasticsearch delete request rate (requests per second).

Throttling is not used by default.

#### `elasticsearch-scroll-batch-rate`

`--elasticsearch-scroll-batch-rate` `RATE`

Set the Elasticsearch scroll batch rate (batches per second).

Throttling is not used by default.

#### `elasticsearch-scroll-batch-size`

`--elasticsearch-scroll-batch-size` `SIZE`

Set the Elasticsearch scroll batch size.

Default: `100000`

#### exclude-paths

`-e`, `--exclude-paths` `PATHS`

A semicolon-separated list of paths to exclude from processing.

See [`path(s)`](#paths) for more details.

#### `from`

`-f`, `--from` `FROM`

Set from time in the
[Unix (POSIX, epoch) time](https://en.wikipedia.org/wiki/Unix_time) format.

Example: `1420070400`

#### `jobs`

`-j`, `--jobs` `JOBS`

Set the number of jobs to run simultaneously.

#### `log-file`

`-l`, `--log-file` `FILE`

Set the log file.

Default: `cyanite-remover.log`

#### `log-level`

`-L`, `--log-level` `LEVEL`

Set the Log level.

Available log levels: `all`, `trace`, `debug`, `info`, `warn`, `error`,
`fatal`, `off`.

Default: `info`

#### `run`

`-r`, `--run`

Force a normal run. **Dry run using on default.**

#### `sort`

`-s`, `--sort`

Sort paths in alphabetical order. From version 0.6.1 output is unsorted by
default.

#### `stop-on-error`

`-S`, `--stop-on-error`

Stop on the first non-fatal error.

### `threshold`

`-T`, `--threshold` `THRESHOLD`

Threshold in seconds. Option is used to search for obsolete data.

See commands [`remove-obsolete-data`](#remove-obsolete-data) and
[`list-obsolete-data`](#list-obsolete-data) for more details.

#### `to`

`-t`, `--to` `TO`

Set until time in the
[Unix (POSIX, epoch) time](https://en.wikipedia.org/wiki/Unix_time) format.

Example: `1421280000`

## Usage Scenarios

### Inspecting

Before removing data, you may want to inspect the data to be removed.

#### Listing Metrics from Cassandra

```bash
cyanite-remover list-metrics my_tenant 60:5356800,900:62208000 \
  "requests.nginx.*;node[3-17].cpu.?" cass1.example.org \
  http://es.example.org:9200
```

See command [`list-metrics`](#list-metrics) for more details.

#### Listing Paths from Elasticsearch

```bash
cyanite-remover list-paths my_tenant "requests.nginx.*;node[3-17].cpu.?" \
  http://es.example.org:9200
```

See command [`list-paths`](#list-paths) for more details.

### Removing

#### Removing Metrics from Cassandra

```bash
cyanite-remover --run --jobs 8 --cassandra-options "{:compression :lz4}" \
  remove-metrics my_tenant 60:5356800,900:62208000 \
  "requests.nginx.*;node[3-17].cpu.?" cass1.example.org \
  http://es.example.org:9200
```

See command [`remove-metrics`](#remove-metrics) for more details.

**Before removing data, [make sure](#listing-metrics-from-cassandra) that you
are going to remove the desired data!**

#### Removing Paths from Elasticsearch

```bash
cyanite-remover --run remove-paths my_tenant \
  "requests.nginx.*;node[3-17].cpu.?" http://es.example.org:9200
```

See command [`remove-paths`](#remove-paths) for more details.

**Before removing data, [make sure](#listing-paths-from-elasticsearch) that you
are going to remove the desired data!**

**Always remove metrics first. Deletion of paths will make it impossible to
remove relevant metrics!**

## License

cyanite-remover is covered by [MIT License](https://raw.githubusercontent.com/cybem/cyanite-remover/master/LICENSE)

## Thanks

Thanks to [Pierre-Yves Ritschard](https://github.com/pyr) aka @pyr for his work
on [Cyanite](https://github.com/pyr/cyanite)
