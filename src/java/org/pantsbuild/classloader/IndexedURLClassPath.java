//  Copyright 2012 Foursquare Labs Inc. All Rights Reserved

package org.pantsbuild.classloader;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLStreamHandlerFactory;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import sun.misc.Resource;
import sun.misc.URLClassPath;

/**
 * A modified URLClassPath that indexes the contents of classpath elements, for faster resource locating.
 *
 * The standard URLClassPath does a linear scan of the classpath for each resource, which becomes
 * prohibitively expensive for classpaths with many elements.
 */
public class IndexedURLClassPath extends URLClassPath {
  public IndexedURLClassPath(URL[] urls, URLStreamHandlerFactory factory, Boolean strict, Boolean verbose) {
    super(urls, factory);

    this.factory = factory;
    this.strict = strict;
    this.verbose = verbose;

    for (int i = 0; i < urls.length; ++i) {
      indexURL(urls[i]);
    }
  }

  public IndexedURLClassPath(URL[] urls, Boolean strict, Boolean verbose) {
    this(urls, null, strict, verbose);
  }

  public void addURL(URL url) {
    indexURL(url);
  }

  @Override
  public Resource getResource(final String name, boolean check) {
    URLClassPath delegate = index.get(name);
    if (delegate == null) {
      return null;
    }
    if (verbose) {
      System.out.println("IndexedURLClassPath getting resource " + name);
    }
    return delegate.getResource(name, check);
  }

  @Override
  public URL findResource(final String name, boolean check) {
    URLClassPath delegate = index.get(name);
    if (delegate == null) {
      return null;
    }
    if (verbose) {
      System.out.println("IndexedURLClassPath finding resource " + name);
    }
    return delegate.findResource(name, check);
  }

  private void indexURL(URL url) {
    if (verbose) {
      System.out.println("IndexedURLClassPath indexing " + url);
    }
    try {
      if (!"file".equals(url.getProtocol())) {
        throw new RuntimeException("Classpath element is not a file: " + url);
      }
      File root = new File(url.getPath());
      URL[] args = { url };
      URLClassPath delegate = new URLClassPath(args, factory);

      if (root.isDirectory()) {
        String rootPath = root.getPath();
        if (!rootPath.endsWith(File.separator)) {
          rootPath = rootPath + File.separator;
        }
        addFilesToIndex(rootPath.length(), root, delegate);
      } else if (root.isFile() && (root.getName().endsWith(".zip") || root.getName().endsWith(".jar"))) {
        ZipFile zipfile = null;
        try {
          zipfile = new ZipFile(root);
          for (Enumeration<? extends ZipEntry> e = zipfile.entries(); e.hasMoreElements(); ) {
            maybeIndexResource(e.nextElement().getName(), delegate);
          }
        } finally {
          if (zipfile != null) {
            zipfile.close();
          }
        }
    }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void addFilesToIndex(int basePrefixLen, File f, URLClassPath delegate) throws IOException {
    if (f.isDirectory()) {
      if (f.getPath().length() > basePrefixLen) {  // Don't index the root itself.
        String relPath = f.getPath().substring(basePrefixLen);
        if (!relPath.endsWith(File.separator)) {
          relPath = relPath + File.separator;
        }
        maybeIndexResource(relPath, delegate);
      }
      File[] directoryEntries = f.listFiles();
      assert(directoryEntries != null);
      for (int i = 0; i < directoryEntries.length; ++i) {
        addFilesToIndex(basePrefixLen, directoryEntries[i], delegate);
      }
    } else {
      String relPath = f.getPath().substring(basePrefixLen);
      maybeIndexResource(relPath, delegate);
    }
  }

  private void maybeIndexResource(String relPath, URLClassPath delegate) {
    if (!index.containsKey(relPath)) {
      index.put(relPath, delegate);
      // Callers may request the directory itself as a resource, and may
      // do so with or without trailing slashes.  We do this in a while-loop
      // in case the classpath element has multiple superfluous trailing slashes.
      if (relPath.endsWith(File.separator)) {
        maybeIndexResource(relPath.substring(0, relPath.length() - File.separator.length()), delegate);
      }
    } else if (strict) {
      throw new RuntimeException(String.format("Resource %s in %s is also in %s", relPath,
          delegate.getURLs()[0], index.get(relPath).getURLs()[0]));
    }
  }

  // Our ctor argument, saved here so we can pass it to the delegates' ctors.
  private final URLStreamHandlerFactory factory;

  // Whether we fail or silently continue if a resource is defined in more than one classpath entry.
  private final boolean strict;

  // Whether we print out verbose information about each resource we handle.
  private final boolean verbose;

  // Map from resource name to URLClassPath to delegate loading that resource to.
  private final Map<String, URLClassPath> index = new HashMap<String, URLClassPath>();
}
