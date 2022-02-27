package io.github.pxzxj;

import git4idea.GitLocalBranch;
import git4idea.branch.GitBranchIncomingOutgoingManager;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class RefreshLabelGitRepositoryChangeListener implements GitRepositoryChangeListener {
    @Override
    public void repositoryChanged(@NotNull GitRepository repository) {
        String branchText = GitBranchUtil.getDisplayableBranchText(repository);
        GitBranchIncomingOutgoingManager incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(repository.getProject());
        boolean hasIncomingFor = incomingOutgoingManager.hasIncomingFor(repository, branchText);
        boolean hasOutgoingFor = incomingOutgoingManager.hasOutgoingFor(repository, branchText);
        if(hasIncomingFor) {
            Collection<GitLocalBranch> branchesWithIncoming = incomingOutgoingManager.getBranchesWithIncoming(repository);
        }
        if(hasOutgoingFor) {
            Collection<GitLocalBranch> branchesWithOutgoing = incomingOutgoingManager.getBranchesWithOutgoing(repository);

        }
    }
}
