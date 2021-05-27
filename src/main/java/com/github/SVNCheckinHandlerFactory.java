package com.github;


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SVNCheckinHandlerFactory extends VcsCheckinHandlerFactory {

    private final Logger LOG = Logger.getInstance(SVNCheckinHandlerFactory.class);

    public SVNCheckinHandlerFactory(){
        super(SvnVcs.getKey());
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
        return new MyCheckinHandler(project, files);
    }

}
