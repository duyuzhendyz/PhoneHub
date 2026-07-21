package com.android.tools.build.bundletool.model.utils.files;

import com.android.tools.build.bundletool.model.utils.OsPlatform;
import com.google.common.base.StandardSystemProperty;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileUtils {
    private static final Pattern HOME_DIRECTORY_ALIAS = Pattern.compile("^~");

    public static Path getPath(String path) {
        if (!OsPlatform.getCurrentPlatform().equals(OsPlatform.WINDOWS)) {
            path = HOME_DIRECTORY_ALIAS.matcher(path)
                .replaceFirst(Matcher.quoteReplacement(StandardSystemProperty.USER_HOME.value()));
        }
        try {
            return Paths.get(path);
        } catch (InvalidPathException e) {
            return Paths.get(path.replace(':', '_'));
        }
    }
}