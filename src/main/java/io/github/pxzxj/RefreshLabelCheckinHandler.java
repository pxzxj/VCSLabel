package io.github.pxzxj;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public class RefreshLabelCheckinHandler extends CheckinHandler {

    private final List<VirtualFile> files;

    private final VCSLabelService vcsLabelService;

    public RefreshLabelCheckinHandler(Project project, List<VirtualFile> files){
        vcsLabelService =  ServiceManager.getService(project, VCSLabelService.class);
        this.files = files;
    }

    @Override
    public void checkinSuccessful() {
        ApplicationManager.getApplication().invokeLater(() -> {
            for(VirtualFile file : files){
                vcsLabelService.evict(file);
            }
            vcsLabelService.refreshProjectView();
        });
    }

}
