mkdir -p ./classes

javac -d ./classes src/java/org/pantsbuild/classloader/IndexedURLClassPath.java src/java/org/pantsbuild/classloader/IndexedURLClassLoader.java

jar -cvf ./indexed-classloader.jar -C ./classes .
