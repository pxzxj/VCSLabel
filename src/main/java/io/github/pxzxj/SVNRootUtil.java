package io.github.pxzxj;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

public class SVNRootUtil {

    private final static Logger LOG = Logger.getInstance(SVNRootUtil.class);

    public static final @NonNls String DOT_SVN = ".svn";

    public static boolean isSVNRoot(@NotNull @NonNls String rootDir) {
        try {
            return isSVNRoot(Paths.get(rootDir));
        }
        catch (InvalidPathException e) {
            LOG.warn(e.getMessage());
            return false;
        }
    }

    public static boolean isSVNRoot(@NotNull Path rootDir) {
        Path dotSvn = rootDir.resolve(DOT_SVN);
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(dotSvn, BasicFileAttributes.class);
        }
        catch (IOException ignore) {
            return false;
        }
        return attributes.isDirectory();
    }
}
