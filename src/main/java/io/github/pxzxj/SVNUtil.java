package io.github.pxzxj;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            return findSVNRootFor(virtualFileToNioPath(vFile)) != null;
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
                    return LocalFileSystem.getInstance().findFileByPath(path.toAbsolutePath().toString());
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

    public static @NotNull Path virtualFileToNioPath(VirtualFile virtualFile) {
        String path = virtualFile.getPath();
        if (StringUtil.endsWithChar(path, ':') && path.length() == 2 && SystemInfo.isWindows) {
            // makes 'C:' resolve to a root directory of the drive C:, not the current directory on that drive
            path += '/';
        }
        return Paths.get(path);
    }
}
