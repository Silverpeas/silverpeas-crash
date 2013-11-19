/*
 * Copyright (C) 2000-2013 Silverpeas
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * As a special exception to the terms and conditions of version 3.0 of
 * the GPL, you may redistribute this Program in connection with Writer Free/Libre
 * Open Source Software ("FLOSS") applications as described in Silverpeas's
 * FLOSS exception.  You should have recieved a copy of the text describing
 * the FLOSS exception, and it is also available here:
 * "http://www.silverpeas.org/legal/licensing"
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.silverpeas.crash;

import com.stratelia.webactiv.util.GeneralPropertiesManager;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.Provider;
import java.security.Security;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import static java.io.File.separatorChar;

/**
 * Web application lifecycle listener.
 *
 * @author mmoquillon
 */
public class ExternalLibLoader implements ServletContextListener {

  /**
   * Classes to load for bypassing the JBoss VFS interferences.
   */
  private static final String CLASSES_TO_LOAD = "bypass.jboss.vfs";

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    Logger.getLogger(getClass().getName()).log(Level.INFO, "Silverpeas Crash starting...");
    loadExternalJarLibraries();
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
  }

  /**
   * Loads all the JAR libraries available in the SILVERPEAS_HOME/repository/lib directory by using
   * our own classloader so that we avoid JBoss loads them with its its asshole VFS.
   */
  private static void loadExternalJarLibraries() {
    String libPath = System.getenv("SILVERPEAS_HOME") + separatorChar + "repository" + separatorChar
        + "lib";
    File libDir = new File(libPath);
    File[] jars = libDir.listFiles();
    URL[] jarURLs = new URL[jars.length];
    try {
      for (int i = 0; i < jars.length; i++) {
        jarURLs[i] = jars[i].toURI().toURL();
      }
      addURLs(jarURLs);
      String[] classNames = GeneralPropertiesManager.getString(CLASSES_TO_LOAD).split(",");
      for (String className : classNames) {
        try {
          Class aClass = ClassLoader.getSystemClassLoader().loadClass(className);
          Class<? extends Provider> jceProvider = aClass.asSubclass(Provider.class);
          Security.insertProviderAt(jceProvider.newInstance(), 0);
        } catch (Throwable t) {
          Logger.getLogger(ExternalLibLoader.class.getSimpleName()).log(Level.SEVERE,
              t.getMessage(), t);
        }
      }
    } catch (Exception ex) {
      Logger.getLogger(ExternalLibLoader.class.getSimpleName()).log(Level.SEVERE,
          ex.getMessage(), ex);
    }
  }

  private static void addURLs(URL[] urls) throws NoSuchMethodException, IllegalArgumentException,
      IllegalAccessException, InvocationTargetException {
    ClassLoader cl = ClassLoader.getSystemClassLoader();
    if (cl instanceof URLClassLoader) {
      URLClassLoader urlClassloader = (URLClassLoader) cl;
      // addURL is a protected method, but we can use reflection to call it
      Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
      // change access to true, otherwise, it will throw exception
      method.setAccessible(true);
      for (URL url : urls) {
        method.invoke(urlClassloader, new Object[]{url});
      }
    } else {
      // SystemClassLoader is not URLClassLoader....
    }
  }
}
