# akka-topics-java
Akka in Action 2nd edition examples in Java

To run an example, go to the appropriate folder and run
`../mvnw compile exec:java -Dexec.mainClass=<name>`
where `<name>` is the className of the example you want.
For example if you want to run WalletApp from chapter 2, do:
``` console
$ cd up-and-running
$ ../mvnw compile exec:java -Dexec.mainClass=WalletApp
```

Some examples are in packages. This is done to avoid class name clashes.
So to run them, you must provie full className.
e.g. for running ErrorKernelApp in chapter 3:
``` console
$ cd one-actor-is-no-actor
$ ../mvnw compile exec:java -Dexec.mainClass=errorkernel.ErrorKernelApp
```

Java 17 is required.
If you're on windows, run `mvnw.cmd` instead of `./mvnw`. If you have Maven 3.8.6, you can use your own `mvn` executable.

The original repo is [here](https://github.com/franciscolopezsancho/akka-topics).
