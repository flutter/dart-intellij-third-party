package com.jetbrains.dart.analysisServer;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import com.jetbrains.lang.dart.ide.marker.DartServerImplementationsMarkerProvider;
import com.jetbrains.lang.dart.psi.DartComponentName;

import java.util.ArrayList;
import com.intellij.icons.AllIcons;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.TimeoutUtil;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class DartServerImplementationsMarkerProviderTest extends CodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    DartTestUtils.configureDartSdk(myModule, myFixture.getTestRootDisposable(), true);
    myFixture.setTestDataPath(DartTestUtils.BASE_TEST_DATA_PATH + getBasePath());
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
  }

  @Override
  protected String getBasePath() {
    return "/analysisServer/implementationsMarker";
  }

  private void checkHasGutterAtCaret(final String expectedText, final Icon expectedIcon) {
    final String testName = getTestName(false);
    myFixture.configureByFile(testName + ".dart");

    List<AnalyticsData> events = new ArrayList<>();
    Analytics.withReportObserver(events::add, () -> {
      myFixture.doHighlighting(); // make sure server is warmed up

      if (!someGutterHasIcon(myFixture.findGuttersAtCaret(), expectedIcon)) {
        TimeoutUtil.sleep(200); // wait a bit for info about line markers 'up' to arrive
      }

      DartServerOverrideMarkerProviderTest.checkGutter(myFixture.findGuttersAtCaret(), expectedText, expectedIcon);
    });
    assertTrue("Marker construction must not report", events.isEmpty());
  }

  private static boolean someGutterHasIcon(@NotNull final List<GutterMark> gutters, @NotNull final Icon icon) {
    for (GutterMark gutter : gutters) {
      if (icon.equals(gutter.getIcon())) {
        return true;
      }
    }
    return false;
  }

  public void testClickAfterTargetsDisappear() throws Exception {
    myFixture.configureByText("empty.dart", "class Empty {}");
    myFixture.doHighlighting();
    DartComponentName name = PsiTreeUtil.findChildOfType(myFixture.getFile(), DartComponentName.class);
    // Recreate a stale marker without requiring an asynchronous server-update race.
    var factory = DartServerImplementationsMarkerProvider.class.getDeclaredMethod("createMarkerClass", DartComponentName.class);
    factory.setAccessible(true);
    LineMarkerInfo<?> marker = (LineMarkerInfo<?>)factory.invoke(null, name);
    DartServerOverrideMarkerProviderTest.checkGutter(List.of(marker.createGutterRenderer()), "Has subclasses", AllIcons.Gutter.OverridenMethod);
  }

  public void testClassExtended() {
    checkHasGutterAtCaret("Has subclasses", AllIcons.Gutter.OverridenMethod);
  }

  public void testClassImplemented() {
    checkHasGutterAtCaret("Has subclasses", AllIcons.Gutter.OverridenMethod);
  }

  public void testMethodExtended() {
    checkHasGutterAtCaret("Is overridden in subclasses", AllIcons.Gutter.OverridenMethod);
  }

  public void testMethodImplemented() {
    checkHasGutterAtCaret("Is overridden in subclasses", AllIcons.Gutter.OverridenMethod);
  }

  public void testOperatorOverridden() {
    checkHasGutterAtCaret("Is overridden in subclasses", AllIcons.Gutter.OverridenMethod);
  }
}
