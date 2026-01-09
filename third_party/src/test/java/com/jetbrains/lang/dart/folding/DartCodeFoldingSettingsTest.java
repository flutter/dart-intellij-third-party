package com.jetbrains.lang.dart.folding;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class DartCodeFoldingSettingsTest extends BasePlatformTestCase {

  public void testGetInstance() {
    DartCodeFoldingSettings settings = DartCodeFoldingSettings.getInstance();
    assertNotNull("DartCodeFoldingSettings instance should not be null", settings);
  }

  public void testDefaultValues() {
    DartCodeFoldingSettings settings = new DartCodeFoldingSettings();
    assertTrue("Collapse parts should be true by default", settings.isCollapseParts());
    assertFalse("Collapse generic parameters should be false by default", settings.isCollapseGenericParameters());
  }

  public void testCollapseParts() {
    DartCodeFoldingSettings settings = new DartCodeFoldingSettings();
    
    // Initial check
    assertTrue(settings.isCollapseParts());
    
    // Set to false
    settings.setCollapseParts(false);
    assertFalse("Collapse parts should be false after setting", settings.isCollapseParts());
    
    // Set back to true
    settings.setCollapseParts(true);
    assertTrue("Collapse parts should be true after setting", settings.isCollapseParts());
  }

  public void testCollapseGenericParameters() {
    DartCodeFoldingSettings settings = new DartCodeFoldingSettings();
    
    // Initial check
    assertFalse(settings.isCollapseGenericParameters());
    
    // Set to true
    settings.setCollapseGenericParameters(true);
    assertTrue("Collapse generic parameters should be true after setting", settings.isCollapseGenericParameters());
    
    // Set back to false
    settings.setCollapseGenericParameters(false);
    assertFalse("Collapse generic parameters should be false after setting", settings.isCollapseGenericParameters());
  }

  public void testGetState() {
    DartCodeFoldingSettings settings = new DartCodeFoldingSettings();
    assertSame("getState() should return the instance itself", settings, settings.getState());
  }

  public void testLoadState() {
    DartCodeFoldingSettings targetSettings = new DartCodeFoldingSettings();
    DartCodeFoldingSettings sourceSettings = new DartCodeFoldingSettings();
    
    // Configure source settings
    sourceSettings.setCollapseParts(false);
    sourceSettings.setCollapseGenericParameters(true);
    
    // Load state
    targetSettings.loadState(sourceSettings);
    
    // Verify target settings match source
    assertFalse("Collapse parts should be loaded as false", targetSettings.isCollapseParts());
    assertTrue("Collapse generic parameters should be loaded as true", targetSettings.isCollapseGenericParameters());
    
    // Verify independent modification
    sourceSettings.setCollapseParts(true);
    assertFalse("Target settings should remain unchanged", targetSettings.isCollapseParts());
  }
}
