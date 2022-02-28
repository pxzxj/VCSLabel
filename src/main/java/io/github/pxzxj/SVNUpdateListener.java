package io.github.pxzxj;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import com.intellij.openapi.vcs.update.UpdatedFilesListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;

import java.io.File;
import java.util.Set;

public class SVNUpdateListener implements UpdatedFilesListener {

    private final Logger LOG = Logger.getInstance(SVNUpdateListener.class);

    private Project project;

    public SVNUpdateListener(){

    }

    public SVNUpdateListener(Project project){
        this.project = project;
    }


    @Override
    public void consume(Set<String> strings) {
        LOG.warn("Get a Update Notification!");
        ApplicationManager.getApplication().invokeLater(() -> {
            LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
            VCSLabelService labelService = ServiceManager.getService(project, VCSLabelService.class);

            for(String path : strings){
                VirtualFile file = localFileSystem.findFileByPath(FilePathsHelper.convertPath(path));
                if(file != null){
                    labelService.evict(file);
                }
            }
            labelService.refreshProjectView();
        });
    }

}
