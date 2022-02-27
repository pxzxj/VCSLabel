package io.github.pxzxj;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GitCheckinHandlerFactory extends VcsCheckinHandlerFactory {

    public GitCheckinHandlerFactory(){
        super(GitVcs.getKey());
    }

    @NotNull
    @Override
    protected CheckinHandler createVcsHandler(CheckinProjectPanel panel) {
        Collection<Change> selectedChanges = panel.getSelectedChanges();
        List<VirtualFile> files = new ArrayList<>();
        Project project = panel.getProject();
        for(Change change : selectedChanges){
            VirtualFile virtualFile = change.getVirtualFile();
            if(virtualFile != null){
                files.add(virtualFile);
            }
        }
        return new RefreshLabelCheckinHandler(project, files);
    }

}
