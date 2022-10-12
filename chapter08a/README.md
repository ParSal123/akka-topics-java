We use *jshell* for REPL console.
Jshell is an interactive java console.
```shell
../mvnw compile jshell:run -Dport=<somenumber>
```
After that you can run the code in Main.java:
```java
> /open src/main/java/Main.java
> Main.main(null)
```

If you want to execute AkkaManagement examples,
you can pass the properties as well.
For example:

```shell
../mvnw compile jshell:run -Dport=<number> -Dakka.management.http.port=<number> -Dakka.management.http.hostname=<string>
```

The default values are:

```
akka.management.http.port: 8558
akka.management.http.hostname: "127.0.0.1"
```

For some examples you may have to type the commands yourself,
because you don't have access to `guardian` variable inside `Main.main`.