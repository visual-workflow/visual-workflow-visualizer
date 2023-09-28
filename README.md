Use
---

This is a POC grade project that is not intended for production use.
 

```shell

mvn package exec:java -DskipTests \
   -Dexec.mainClass=com.kgignatyev.temporal.visualwf.renderer.VisualizeWF  \
   -Dexec.args=<workflow id> \
```
