# indexed-classloader
A custom JVM classloader that indexes classpath elements for much faster class/resource location.

- To build: `./make-jar.sh`

- To use, add `indexed-classpath.jar` to the classpath and set
 
  `-Djava.system.class.loader=org.pantsbuild.classloader.IndexedURLClassLoader`.

- Add `-verbose:class` to see debug information from the classloaders.
