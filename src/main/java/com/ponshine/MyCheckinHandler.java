package com.ponshine;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MyCheckinHandler extends CheckinHandler {

    private final Logger LOG = Logger.getInstance(MyCheckinHandler.class);

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
