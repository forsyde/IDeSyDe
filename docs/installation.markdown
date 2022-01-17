---
layout: default
title: Installation
nav_order: 2
---

# Pre-requisites

## Java

IDeSyDe requires a at least Java Runtime Environment (JRE) for Java 8
installed in the machine to run. On Windows, 
the [oracle distribution](https://www.java.com/en/download/manual.jsp) works
fine. On linux, it is better to follow the java installation instructions
for your specific distro.

## MiniZinc (Optional)

IDeSyDe uses MiniZinc as one backend for solving problems that it identifies.
Following the instructions at the [MiniZinc page](https://www.minizinc.org/)
and making the `minizinc` binary callable is enough for IDeSyDe to use it.

# Installing compiled IDeSyDe

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

Coming soon.