# cyanite-remover

[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://raw.githubusercontent.com/cybem/whisper2cyanite/master/LICENSE)
[![Build Status](https://travis-ci.org/cybem/cyanite-remover.svg?branch=master)](https://travis-ci.org/cybem/cyanite-remover)
[![Dependencies Status](http://jarkeeper.com/cybem/cyanite-remover/status.svg)](http://jarkeeper.com/cybem/cyanite-remover)

cyanite-remover is a [Cyanite](https://github.com/pyr/cyanite) data removal
tool.

## Table of Contents

* [Building](#building)
  * [Dependencies](#dependencies)
  * [Building a Standalone JAR-file](#building-a-standalone-jar-file)
  * [Building a Deb-package](#building-a-deb-package)

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
