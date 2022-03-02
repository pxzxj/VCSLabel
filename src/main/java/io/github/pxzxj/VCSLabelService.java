package io.github.pxzxj;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.vcs.log.Hash;
import git4idea.GitFileRevision;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchesCollection;
import git4idea.history.GitHistoryProvider;
import git4idea.repo.GitRepository;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.jetbrains.idea.svn.info.Info;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public final class VCSLabelService implements Disposable {

    private final Logger LOG = Logger.getInstance(VCSLabelService.class);

    private final Project project;
    private VirtualFile projectVirtualFile;
    private final Locale locale;
    private SvnVcs svnVcs;
    private GitVcs gitVcs;
    private GitHistoryProvider vcsHistoryProvider;
    private GitRepository gitRepository;
    private String currentBranchName;
    private final ConcurrentHashMap<String, String> latestBranchRevision = new ConcurrentHashMap<>();

    private final VcsContextFactory vcsContextFactory;
    private final BlockingQueue<VirtualFile> pendingFileQueue = new LinkedBlockingDeque<>();
    private final Set<String> calculatingFileSet = new CopyOnWriteArraySet<>();
    private final ConcurrentHashMap<String, String> labelCache = new ConcurrentHashMap<>();
    private final AtomicInteger calculatorCount = new AtomicInteger();
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    private final ExecutorService handlerService = Executors.newSingleThreadExecutor();

    public VCSLabelService(Project project){
        this.project = project;
        locale = Locale.getDefault() != null ? Locale.getDefault() : Locale.US;
        vcsContextFactory = VcsContextFactory.SERVICE.getInstance();
        String projectPath = project.getBasePath();
        if(projectPath != null) {
            projectVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(projectPath));
            svnVcs = SvnVcs.getInstance(project);
            if(svnVcs != null && !SVNRootUtil.isSVNRoot(projectPath)) {
                svnVcs = null;
            }
            gitVcs = (GitVcs)ProjectLevelVcsManager.getInstance(project).findVcsByName(GitVcs.NAME);
            ProgressManager.checkCanceled();
            if(gitVcs != null) {
                if(GitUtil.isGitRoot(projectPath)) {
                    vcsHistoryProvider = gitVcs.getVcsHistoryProvider();
                } else {
                    gitVcs = null;
                }
            }
        }
    }

    public void decorateVcsTag(ProjectViewNode<?> node, PresentationData data){
        VirtualFile vFile =  node.getVirtualFile();
        String vcsMessage = null;
        if((svnVcs != null || gitVcs != null) && vFile != null){
            vcsMessage = getCache(vFile);
            if(vcsMessage == null){
                pendingFileQueue.add(vFile);
                if(calculatorCount.get() < 6){
                    calculatorCount.incrementAndGet();
                    executorService.submit(new LabelCalculator());
                }
            }
        } else if(vFile != null){
            vcsMessage = "";
            addCache(vFile, "");
        }
        if(vcsMessage != null && !"".equals(vcsMessage)){
            data.addText(vcsMessage, SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
    }

    public void addCache(VirtualFile vFile, String cache){
        if(cache == null){
            cache = "";
        }
        String key = vFile.getPath();
        labelCache.put(key, cache);
    }

    public String getCache(VirtualFile vFile) {
        String key = vFile.getPath();
        return labelCache.get(key);
    }

    public void evict(VirtualFile file) {
        String key = file.getPath();
        labelCache.remove(key);
    }

    public void refreshProjectView() {
        ApplicationManager.getApplication().invokeLater(() -> {
            final ProjectView projectView = ProjectView.getInstance(project);
            if(project.isOpen()) {
                projectView.refresh();
            }
        });
    }

    public void handleGitChangeEvent(GitRepository gp) {
        if(gitRepository == null) {
            gitRepository = gp;
        }
        handlerService.submit(new GitRepositoryChangeHandler());
    }

    @Override
    public void dispose() {
        pendingFileQueue.clear();
        calculatingFileSet.clear();
        labelCache.clear();
        executorService.shutdown();
        handlerService.shutdown();
    }

    private class LabelCalculator implements Runnable{

        private CountDownLatch latch;

        public LabelCalculator() {
        }

        public LabelCalculator(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void run() {
            VirtualFile file = null;
            try {
                int poolCount = 0;
                file = pendingFileQueue.poll(1, TimeUnit.SECONDS);
                SimpleDateFormat df = new SimpleDateFormat("yy-MM-dd aHH:mm", locale);
                while(file != null){
                    if(!calculatingFileSet.add(file.getPath())) {
                        continue;
                    }
                    poolCount++;
                    String vcsMessage = "";
                    if(svnVcs != null) {
                        Info info = svnVcs.getInfo(file);
                        if(info != null){
                            CommitInfo commitInfo = info.getCommitInfo();
                            String author = commitInfo.getAuthor();
                            long revisionNumber = commitInfo.getRevisionNumber();
                            Date date = commitInfo.getDate();
                            if(date != null){
                                vcsMessage = " " + revisionNumber + " " + df.format(date) + " " + author;
                            }
                        }
                    } else if(gitVcs != null) {
                        FilePath filePath = vcsContextFactory.createFilePathOn(file);
                        GitFileRevision lastRevision = (GitFileRevision) vcsHistoryProvider.getLastRevision(filePath);
                        if(lastRevision != null) {
                            String shortRevision = lastRevision.getRevisionNumber().asString().substring(0, 8);
                            String committerName = lastRevision.getCommitterName();
                            Date commitDate = lastRevision.getRevisionDate();
                            if(committerName != null && commitDate != null) {
                                String author = lastRevision.getAuthor();
                                if(!committerName.equals(author) && author != null) {
                                    committerName = committerName + "(" + author + ")";
                                }
                                vcsMessage = " " + shortRevision + " " + df.format(commitDate) + " " + committerName;
                            }
                        }
                    }
                    addCache(file, vcsMessage);
                    calculatingFileSet.remove(file.getPath());
                    file = pendingFileQueue.poll(1, TimeUnit.SECONDS);
                }
                if(poolCount > 0){
                    LOG.debug("Refresh Project View: " + pendingFileQueue.size());
                    LOG.debug("Cache Size: " + labelCache.size());
                    refreshProjectView();
                }
            } catch (Exception e) {
                e.printStackTrace();
                if(file != null) {
                    calculatingFileSet.remove(file.getPath());
                }
            } finally {
                LOG.debug("current calculator count: " + calculatorCount.decrementAndGet());
                if(latch != null) {
                    latch.countDown();
                }
            }
        }
    }

    private class GitRepositoryChangeHandler implements Runnable {

        @Override
        public void run() {
            GitBranchesCollection branches = gitRepository.getBranches();
            String oldCurrentBranchName = currentBranchName;
            currentBranchName = gitRepository.getCurrentBranchName();
            if(currentBranchName == null) {
                return;
            }
            boolean branchChanged = oldCurrentBranchName != null && !oldCurrentBranchName.equals(currentBranchName);
            if(branchChanged) {
                labelCache.clear();
                refreshProjectView();
            }
            for(GitLocalBranch gitLocalBranch : branches.getLocalBranches()) {
                String name = gitLocalBranch.getName();
                Hash hash = branches.getHash(gitLocalBranch);
                String revision = hash != null ? hash.asString().substring(0, 16) : "";
                String oldRevision = latestBranchRevision.put(name, revision);
                if(oldRevision != null && !branchChanged && !revision.equals(oldRevision) && name.equals(currentBranchName)) {
                    try {
                        List<CommittedChangeList> committedChanges = getCommittedChanges(revision, oldRevision);
                        Set<VirtualFile> changedFileSet = new HashSet<>();
                        for(CommittedChangeList committedChangeList : committedChanges) {
                            Collection<Change> changes = committedChangeList.getChanges();
                            for(Change change : changes) {
                                VirtualFile virtualFile = change.getVirtualFile();
                                if(virtualFile != null) {
                                    changedFileSet.add(virtualFile);
                                }
                            }
                        }
                        if(!changedFileSet.isEmpty()) {
                            pendingFileQueue.addAll(changedFileSet);
                            CountDownLatch latch = new CountDownLatch(5);
                            for (int i = 0; i < 5; i++) {
                                calculatorCount.incrementAndGet();
                                executorService.submit(new LabelCalculator(latch));
                            }
                            latch.await();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private List<CommittedChangeList> getCommittedChanges(String beforeRev, String afterRev) {
            List<CommittedChangeList> result = new ArrayList<>();
            try {
                GitUtil.getLocalCommittedChanges(project, projectVirtualFile, h -> {
                    if (beforeRev != null && afterRev != null) {
                        h.addParameters(afterRev + ".." + beforeRev);
                    }
                    else if (beforeRev != null) {
                        h.addParameters(beforeRev);
                    }
                    else if (afterRev != null) {
                        h.addParameters(afterRev + "..");
                    }
                }, result::add, false);
            } catch (VcsException e) {
                e.printStackTrace();
            }
            return result;
        }
    }

}
