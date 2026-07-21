import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Patch bundletool FileUtils.class to wrap Paths.get() in try-catch.
 * On InvalidPathException, replace ':' with '_' and retry.
 */
public class PatchFileUtils {
    public static void main(String[] args) throws Exception {
        String jarPath = args.length > 0 ? args[0] : "bundletool-original.jar";
        String classEntry = "com/android/tools/build/bundletool/model/utils/files/FileUtils.class";

        // Read original class
        byte[] classData;
        try (ZipFile zf = new ZipFile(jarPath)) {
            ZipEntry ze = zf.getEntry(classEntry);
            if (ze == null) {
                System.err.println("ERROR: " + classEntry + " not found");
                return;
            }
            try (InputStream is = zf.getInputStream(ze)) {
                classData = is.readAllBytes();
            }
        }
        System.out.println("Original class size: " + classData.length);

        // Find the getPath method bytecode pattern:
        // 2A (aload_0) 03 (iconst_0) BD xx xx (anewarray String) B8 xx xx (invokestatic Paths.get) B0 (areturn)
        int methodStart = -1;
        int anewarrayCp = -1, invokestaticCp = -1;
        for (int i = 0; i < classData.length - 10; i++) {
            if (classData[i] == 0x2A && classData[i+1] == 0x03 && classData[i+2] == 0xBD
                && classData[i+5] == 0xB8 && classData[i+8] == 0xB0) {
                // Verify preceding astore_0 (0x4E) pattern (end of ~ replacement)
                if (i >= 1 && classData[i-1] == 0x4E) {
                    methodStart = i;
                    anewarrayCp = ((classData[i+3] & 0xFF) << 8) | (classData[i+4] & 0xFF);
                    invokestaticCp = ((classData[i+6] & 0xFF) << 8) | (classData[i+7] & 0xFF);
                    System.out.println("Found getPath Paths.get call at offset " + i);
                    System.out.println("  anewarray cp: #" + anewarrayCp);
                    System.out.println("  invokestatic cp: #" + invokestaticCp);
                    break;
                }
            }
        }

        if (methodStart == -1) {
            System.err.println("ERROR: Could not find getPath method pattern");
            return;
        }

        // Parse constant pool to find/add entries
        int cpCount = ((classData[8] & 0xFF) << 8) | (classData[9] & 0xFF);
        System.out.println("Constant pool count: " + cpCount);

        // Find existing constant pool entries we need:
        // - java/nio/file/InvalidPathException class
        // - String.replace(CharSequence, CharSequence) method
        // - ":" and "_" strings
        int invalidPathExcCp = -1;
        int stringReplaceCp = -1;
        int colonStrCp = -1;
        int underscoreStrCp = -1;
        int stringClassCp = -1;
        int pathsClassCp = -1;

        // Walk the constant pool
        int pos = 10;
        Map<Integer, String> cpInfo = new HashMap<>();
        for (int i = 1; i < cpCount; i++) {
            int tag = classData[pos] & 0xFF;
            switch (tag) {
                case 1: // Utf8
                    int len = ((classData[pos+1] & 0xFF) << 8) | (classData[pos+2] & 0xFF);
                    String s = new String(classData,