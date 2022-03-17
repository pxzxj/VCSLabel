package io.github.pxzxj;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class SVNUtil {

    private final static Logger LOG = Logger.getInstance(SVNUtil.class);

    public static final @NonNls String DOT_SVN = ".svn";

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

    public static boolean isUnderSVN(@NotNull VirtualFile vFile) {
        try {
            return findSVNRootFor(vFile.toNioPath()) != null;
        }
        catch (InvalidPathException e) {
            LOG.warn(e.getMessage());
            return false;
        }
    }

    @Nullable
    public static VirtualFile findSVNRootFor(@NotNull Path path) {
        try {
            Path root = path;
            while (root != null) {
                if (isSVNRoot(root)) {
                    return LocalFileSystem.getInstance().findFileByNioFile(root);
                }
                root = root.getParent();
            }
            return null;
        }
        catch (InvalidPathException e) {
            LOG.warn(e.getMessage());
            return null;
        }
    }
}
