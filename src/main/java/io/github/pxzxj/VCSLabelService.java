package io.github.pxzxj;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
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
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public final class VCSLabelService implements Disposable {

    private final Logger LOG = Logger.getInstance(VCSLabelService.class);

    private final Project project;
    private final FilePath projectFilePath;
    private final Locale locale;
    private SvnVcs svnVcs;
    private GitVcs gitVcs;
    private GitHistoryProvider vcsHistoryProvider;
    private CommittedChangesProvider<CommittedChangeList, ChangeBrowserSettings> committedChangesProvider;
    private GitRepository gitRepository;
    private RepositoryLocation repositoryLocation;
    private ConcurrentHashMap<String, String> latestBranchRevision = new ConcurrentHashMap<>();

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
        Path projectPath = ProjectUtil.getProjectPath(project.getName());
        projectPath = Path.of("D:\\ideaprojects\\boottest");
        //TODO: 移除临时测试代码 projectPath = Path.of("D:\\ideaprojects\\boottest");
        projectFilePath = new LocalFilePath(projectPath.toAbsolutePath().toString(), true);
        svnVcs = SvnVcs.getInstance(project);
        if(svnVcs != null) {
            Version version = null;
            try {
                version = svnVcs.getCommandLineFactory().createVersionClient().getVersion();
            } catch (SvnBindException e) {
                LOG.debug(e.getMessage());
            }
            if(version == null || !SvnUtil.isSvnVersioned(svnVcs, projectFilePath.getIOFile())) {
                svnVcs = null;
            }
        }
        gitVcs = (GitVcs)ProjectLevelVcsManager.getInstance(project).findVcsByName(GitVcs.NAME);
        ProgressManager.checkCanceled();
        if(gitVcs != null) {
            if(GitUtil.isGitRoot(projectPath)) {
                vcsHistoryProvider = gitVcs.getVcsHistoryProvider();
                committedChangesProvider = gitVcs.getCommittedChangesProvider();
            } else {
                gitVcs = null;
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
                if(calculatorCount.get() < 5){
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
            projectView.refresh();
        });
    }

    public void handleGitChangeEvent(GitRepository gp) {
        if(gitRepository == null) {
            gitRepository = gp;
            repositoryLocation = committedChangesProvider.getLocationFor(projectFilePath);
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

        @Override
        public void run() {
            try {
                int poolCount = 0;
                VirtualFile file = pendingFileQueue.poll(1, TimeUnit.SECONDS);
                SimpleDateFormat df = new SimpleDateFormat("yy-MM-dd aHH:mm", locale);
                while(file != null && calculatingFileSet.add(file.getPath())){
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
                                vcsMessage = "  " + revisionNumber + " " + df.format(date) + " " + author;
                            }
                        }
                    } else if(gitVcs != null) {
                        FilePath filePath = vcsContextFactory.createFilePathOn(file);
                        GitFileRevision lastRevision = (GitFileRevision) vcsHistoryProvider.getLastRevision(filePath);
                        if(lastRevision != null) {
                            String committerName = lastRevision.getCommitterName();
                            Date commitDate = lastRevision.getRevisionDate();
                            if(committerName != null && commitDate != null) {
                                String author = lastRevision.getAuthor();
                                if(!committerName.equals(author) && author != null) {
                                    committerName = committerName + "(" + author + ")";
                                }
                                vcsMessage = "  " + df.format(commitDate) + " " + committerName;
                            }
                        }
                    }
                    addCache(file, vcsMessage);
                    calculatingFileSet.remove(file.getPath());
                    file = pendingFileQueue.poll(1, TimeUnit.SECONDS);
                }
                if(poolCount > 0){
                    //backup solution: compare cache size, refresh when different
                    LOG.debug("Refresh Project View: " + pendingFileQueue.size());
                    LOG.debug("Cache Size: " + labelCache.size());
                    refreshProjectView();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                LOG.debug("current calculator count: " + calculatorCount.decrementAndGet());
            }
        }
    }

    private class GitRepositoryChangeHandler implements Runnable {

        @Override
        public void run() {
            GitBranchesCollection branches = gitRepository.getBranches();
            for(GitLocalBranch gitLocalBranch : branches.getLocalBranches()) {
                String name = gitLocalBranch.getName();
                Hash hash = branches.getHash(gitLocalBranch);
                String revision = hash != null ? hash.asString().substring(0, 16) : "";
                String value = latestBranchRevision.putIfAbsent(name, revision);
                if(value != null && !revision.equals(value)) {
                    ChangeBrowserSettings changeBrowserSettings = new RangeChangeBrowserSettings(value, revision);

                }
            }
        }
    }

    private static class RangeChangeBrowserSettings extends ChangeBrowserSettings {

        private String beforeRevision;
        private String afterRevision;

        public RangeChangeBrowserSettings(String beforeRevision, String afterRevision) {
            this.beforeRevision = beforeRevision;
            this.afterRevision = afterRevision;
        }

        @Override
        public @Nullable Long getChangeBeforeFilter() {
            return Long.parseLong(beforeRevision, 16);
        }

        @Override
        public @Nullable Long getChangeAfterFilter() {
            return Long.parseLong(afterRevision, 16);
        }
    }
}
