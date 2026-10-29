// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.lang.dart.ide.runner.actions.DartPopFrameAction;
import com.jetbrains.lang.dart.ide.runner.server.OpenDartObservatoryUrlAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;

public class ActionUpdateThreadTest extends BasePlatformTestCase {

  public void testAllPluginXmlActions_declareExplicitUpdateThread() throws Exception {
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

    Document doc = null;
    final Enumeration<URL> resources = getClass().getClassLoader().getResources("META-INF/plugin.xml");
    while (resources.hasMoreElements()) {
      final URL url = resources.nextElement();
      try (InputStream stream = url.openStream()) {
        final Document candidate = factory.newDocumentBuilder().parse(stream);
        final NodeList idNodes = candidate.getElementsByTagName("id");
        if (idNodes.getLength() > 0 && "Dart".equals(idNodes.item(0).getTextContent().trim())) {
          doc = candidate;
          break;
        }
      }
    }
    if (doc == null) {
      final InputStream stream = getClass().getClassLoader().getResourceAsStream("META-INF/plugin.xml");
      if (stream != null) {
        try (stream) {
          doc = factory.newDocumentBuilder().parse(stream);
        }
      }
    }
    assertNotNull("Dart META-INF/plugin.xml not found on test classpath", doc);

    final Set<String> classNames = new TreeSet<>();
    final NodeList actions = doc.getElementsByTagName("action");
    final ActionManager actionManager = ActionManager.getInstance();

    for (int i = 0; i < actions.getLength(); i++) {
      final Element element = (Element) actions.item(i);
      final String className = element.getAttribute("class");
      if (!className.isEmpty()) {
        classNames.add(className);
      }
      final String id = element.getAttribute("id");
      if (!id.isEmpty()) {
        final AnAction action = actionManager.getAction(id);
        assertNotNull("Action with id '" + id + "' must be registered in ActionManager", action);
        assertEquals("Action registered with id '" + id + "' must return BGT",
                     ActionUpdateThread.BGT, action.getActionUpdateThread());
      }
    }

    final NodeList groups = doc.getElementsByTagName("group");
    for (int i = 0; i < groups.getLength(); i++) {
      final String className = ((Element) groups.item(i)).getAttribute("class");
      if (!className.isEmpty()) {
        classNames.add(className);
      }
    }

    assertFalse("No action classes found in plugin.xml", classNames.isEmpty());

    for (String className : classNames) {
      final Class<?> clazz = Class.forName(className);
      final AnAction action = (AnAction) clazz.getDeclaredConstructor().newInstance();
      assertEquals("Action/Group " + className + " must return BGT",
                   ActionUpdateThread.BGT, action.getActionUpdateThread());
    }
  }

  public void testStandaloneAndUnregisteredActions_declareExplicitUpdateThread() {
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartPopFrameAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new OpenDartObservatoryUrlAction(null, () -> true));
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartFixAction());
  }

  private static void assertActionUpdateThread(ActionUpdateThread expected, AnAction action) {
    assertNotNull("Action instance must not be null", action);
    assertEquals("Action " + action.getClass().getSimpleName() + " must explicitly specify " + expected,
                 expected,
                 action.getActionUpdateThread());
  }
}
