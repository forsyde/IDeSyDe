<!-- ---
layout: default
title: Installation
nav_order: 1
permalink: /usage/install
parent: Usage
--- -->

## Installing IDeSyDe

Currently the only supported "installation" method is to download and use the IDeSyDe `jar` directly.
Which should be reasonably easy once you have a JVM distribution intalled in your machine. 
The jar can be downloaded from the [IDeSyDe's releases page](https://github.com/forsyde/IDeSyDe/releases). 
Note that the name might note be exactly `idesyde.jar`, but `idesyde-x.x.x-cli.jar` or `IDeSyDe-x.x.x.jar`.

## Mandatory dependencies

### Java

IDeSyDe requires at least a Java Runtime Environment (JRE) for Java 11
installed in the machine to run. On Windows and linux, 
the [oracle distribution](https://www.java.com/en/download/manual.jsp), 
[corretto](https://aws.amazon.com/corretto/?filtered-posts.sort-by=item.additionalFields.createdDate&filtered-posts.sort-order=desc) and [graalvm](https://www.graalvm.org/) have been tested to work.
fine. On linux, it is better to follow the java installation instructions
for your specific distro.

## Optional dependencies

### MiniZinc 

IDeSyDe might use [MiniZinc](https://www.minizinc.org/) as the explorer for some of the design space exploration
scenarios that can be identified. Make sure that minzinc is usable from your terminal so that IDeSyDe is also
able to reach it. For UNIX like systems, this translate to having `minizinc` in your PATH so that, calling `minizinc`,
yields:

    > minizinc   
    minizinc: MiniZinc driver.
    Usage: minizinc  [<options>] [-I <include path>] <model>.mzn [<data>.dzn ...] or just <flat>.fzn
    More info with "minizinc --help"


<!-- # Installing compiled IDeSyDe

Being a JVM first application, IDeSyDe is distributed as a standalone
[jars](https://docs.oracle.com/javase/tutorial/deployment/jar/basicsindex.html).
Therefore, it is enough that you download the latest _jar_ from the 
[releases page](https://github.com/forsyde/IDeSyDe/releases)
and make it available as a callable binary in your machine/OS.

## Linux quick install

You can run the following commands _in order_ at your bash shell to make `idesyde`
available as callable command. 

```
curl --silent "https://api.github.com/repos/forsyde/IDeSyDe/releases/latest" | grep "browser_download_url" | sed -E 's/.*"([^"]+)".*/\1/' | wget -i- -O ~/.local/bin/idesyde.jar
echo '#!/bin/bash\njava -jar ~/.local/bin/idesyde.jar $@' > ~/.local/bin/idesyde
chmod +x ~/.local/bin/idesyde
```

The first line downloads the latest idesyde jar release in the right place.
The second line creates a small bash wrapper so you can call the jar file.
The third line makes the wrapper executable.

## Windows quick install

Coming soon. -->