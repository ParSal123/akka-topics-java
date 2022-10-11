We use *jshell* for REPL console.
```shell
../mvnw compile jshell:run -Dport=<somenumber>
```
After that you can run the code in Main.java:
```java
> /open src/main/java/Main.java
> Main.main(null)
```