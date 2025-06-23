package utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JarUtils {
  private static final Logger logger = Logger.getLogger(JarUtils.class.getName());

  Manifest _manifest;

  public JarUtils() {
    try {
      _manifest = getManifest();
      if (_manifest == null) {
        logger.log(Level.WARNING, "Cannot read Manifest");
      }
    } catch (Exception e1) {
      logger.log(Level.WARNING, "Cannot initialize", e1);
    }
  }

  public String getAttribute(String attributeName) {
    if (_manifest == null) {
      return "-unk-";
    }
    return manifestAttribute(_manifest, attributeName);
    // String buildTime = manifestAttribute(manifest, "Build-Time");
    // String projectVersion = manifestAttribute(manifest, "Project-Version");
  }

  private static String manifestAttribute(final Manifest manifest, final String needle) {
    if (manifest != null) {
      Attributes attr = manifest.getMainAttributes();
      String value =
          attr.keySet().stream()
              .filter(key -> needle.equals(key.toString()))
              .findFirst()
              .map(key -> attr.getValue((Attributes.Name) key))
              .orElse("-not set-");
      return value;
    }
    return "unknown";
  }

  private static Manifest getManifest() throws Exception {
    String classPath =
        JarUtils.class.getResource(JarUtils.class.getSimpleName() + ".class").toString();
    if (!classPath.startsWith("jar")) {
      return null;
    }
    String manifestPath = classPath.replace("utils/JarUtils.class", "META-INF/MANIFEST.MF");

    try (InputStream input =
        URI.create(manifestPath).toURL().openStream() /*new URL().openStream()*/) {
      Manifest manifest = new Manifest(input);
      return manifest;
    } catch (IOException e1) {
      logger.log(Level.WARNING, "Exception reading Manifest", e1);
      e1.printStackTrace();
      return null;
    }
  }
}
