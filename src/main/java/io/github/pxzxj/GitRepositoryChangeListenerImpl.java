package io.github.pxzxj;

import com.intellij.openapi.components.ServiceManager;
import git4idea.GitLocalBranch;
import git4idea.branch.GitBranchIncomingOutgoingManager;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class GitRepositoryChangeListenerImpl implements GitRepositoryChangeListener {
    @Override
    public void repositoryChanged(@NotNull GitRepository repository) {
        VCSLabelService labelService = ServiceManager.getService(repository.getProject(), VCSLabelService.class);
        labelService.handleGitChangeEvent(repository);
    }
}
