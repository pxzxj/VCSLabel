package com.github;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public class MyCheckinHandler extends CheckinHandler {

    private List<VirtualFile> files;

    private SVNLabelService svnLabelService;

    public MyCheckinHandler(Project project, List<VirtualFile> files){
        svnLabelService =  ServiceManager.getService(project, SVNLabelService.class);
        this.files = files;
    }

    @Override
    public void checkinSuccessful() {
        ApplicationManager.getApplication().invokeLater(() -> {
            for(VirtualFile file : files){
                svnLabelService.evict(file);
            }
            svnLabelService.refreshProjectView();
        });
    }

}
