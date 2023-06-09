/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

public class Main {

  public static void main(String[] args) throws Exception {
    System.loadLibrary(args[0]);

    File file = null;
    try {
      file = createTempFile();
      // String codePath = getDexBaseLocation();
      String codePath = System.getenv("DEX_LOCATION") + "/595-profile-saving.jar";
      VMRuntime.registerAppInfo("test.app",
                                file.getPath(),
                                file.getPath(),
                                new String[] {codePath},
                                VMRuntime.CODE_PATH_TYPE_PRIMARY_APK);

      // Test that the profile saves an app method with a profiling info.
      Method appMethod = Main.class.getDeclaredMethod("testAddMethodToProfile",
          File.class, Method.class);
      testAddMethodToProfile(file, appMethod);

      // Delete the file to check that the runtime can save the profile even if the file doesn't
      // exist.
      file.delete();

      // Test that the profile saves a boot class path method with a profiling info.
      Method bootMethod = File.class.getDeclaredMethod("exists");
      if (bootMethod.getDeclaringClass().getClassLoader() != Object.class.getClassLoader()) {
        System.out.println("Class loader does not match boot class");
      }
      testAddMethodToProfile(file, bootMethod);

      // We never expect System.console to be executed before Main.main gets invoked, and therefore
      // it should never be in a profile.
      Method bootNotInProfileMethod = System.class.getDeclaredMethod("console");
      testMethodNotInProfile(file, bootNotInProfileMethod);

      System.out.println("IsForBootImage: " + isForBootImage(file.getPath()));
    } finally {
      if (file != null) {
        file.delete();
      }
    }
  }

  static void testAddMethodToProfile(File file, Method m) {
    // Make sure we have a profile info for this method without the need to loop.
    ensureProfilingInfo(m);
    // Make sure the profile gets saved.
    ensureProfileProcessing();
    // Verify that the profile was saved and contains the method.
    if (!presentInProfile(file.getPath(), m)) {
      throw new RuntimeException("Expected method " + m + " to be in the profile");
    }
  }

  static void testMethodNotInProfile(File file, Method m) {
    // Make sure the profile gets saved.
    ensureProfileProcessing();
    // Verify that the profile was saved and contains the method.
    if (presentInProfile(file.getPath(), m)) {
      throw new RuntimeException("Did not expect method " + m + " to be in the profile");
    }
  }

  // Ensure a method has a profiling info.
  public static native void ensureProfilingInfo(Method method);
  // Ensures the profile saver does its usual processing.
  public static native void ensureProfileProcessing();
  // Checks if the profiles saver knows about the method.
  public static native boolean presentInProfile(String profile, Method method);
  // Returns true if the profile is for the boot image.
  public static native boolean isForBootImage(String profile);

  private static final String TEMP_FILE_NAME_PREFIX = "temp";
  private static final String TEMP_FILE_NAME_SUFFIX = "-file";

  static native String getProfileInfoDump(
      String filename);

  private static File createTempFile() throws Exception {
    try {
      return File.createTempFile(TEMP_FILE_NAME_PREFIX, TEMP_FILE_NAME_SUFFIX);
    } catch (IOException e) {
      System.setProperty("java.io.tmpdir", "/data/local/tmp");
      try {
        return File.createTempFile(TEMP_FILE_NAME_PREFIX, TEMP_FILE_NAME_SUFFIX);
      } catch (IOException e2) {
        System.setProperty("java.io.tmpdir", "/sdcard");
        return File.createTempFile(TEMP_FILE_NAME_PREFIX, TEMP_FILE_NAME_SUFFIX);
      }
    }
  }

  private static class VMRuntime {
    public static final int CODE_PATH_TYPE_PRIMARY_APK = 1;
    private static final Method registerAppInfoMethod;

    static {
      try {
        Class<? extends Object> c = Class.forName("dalvik.system.VMRuntime");
        registerAppInfoMethod = c.getDeclaredMethod("registerAppInfo",
            String.class, String.class, String.class, String[].class, int.class);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public static void registerAppInfo(
        String packageName,
        String curProfile,
        String refProfile,
        String[] codePaths,
        int codePathsType) throws Exception {
      registerAppInfoMethod.invoke(
          null,
          packageName,
          curProfile,
          refProfile,
          codePaths,
          codePathsType);
    }
  }
}
