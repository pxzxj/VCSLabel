package com.github;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.jetbrains.idea.svn.info.Info;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SVNLabelService implements Disposable {

    private final Logger LOG = Logger.getInstance(SVNLabelService.class);

    private Project project;
    private SvnVcs svnVcs;
    private BlockingQueue<VirtualFile> pendingFileQueue = new LinkedBlockingDeque<>();
    private Set<String> calculatingFileSet = new HashSet<>();
    private ConcurrentHashMap<String, String> labelCache = new ConcurrentHashMap<>();
    private AtomicInteger calculatorCount = new AtomicInteger();
    private ExecutorService executorService = Executors.newFixedThreadPool(5);

    public SVNLabelService(Project project){
        this.project = project;
        svnVcs = SvnVcs.getInstance(project);
    }

    public void decorateSvnTag(ProjectViewNode node, PresentationData data){
        VirtualFile vFile =  node.getVirtualFile();
        String vcsMessage = null;
        if(svnVcs != null && vFile != null){
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
        if(key != null){
            labelCache.put(key, cache);
        }
    }

    public String getCache(VirtualFile vFile) {
        String cache = null;
        String key = vFile.getPath();
        if(key != null){
            cache = labelCache.get(key);
        }
        return cache;
    }

    public void evict(VirtualFile file) {
        String key = file.getPath();
        if(key != null){
            labelCache.remove(key);
        }
    }

    public void refreshProjectView() {
        ApplicationManager.getApplication().invokeLater(() -> {
            final ProjectView projectView = ProjectView.getInstance(project);
            projectView.refresh();
        });
    }

    @Override
    public void dispose() {
        pendingFileQueue.clear();
        calculatingFileSet.clear();
        labelCache.clear();
    }

    class LabelCalculator implements Runnable{

        @Override
        public void run() {
            try {
                int poolCount = 0;
                VirtualFile file = pendingFileQueue.poll(1, TimeUnit.SECONDS);
                while(file != null && calculatingFileSet.add(file.getPath())){
                    poolCount++;
                    Info info = svnVcs.getInfo(file);
                    String vcsMessage = "";
                    if(info != null){
                        CommitInfo commitInfo = info.getCommitInfo();
                        if(commitInfo != null){
                            String author = commitInfo.getAuthor();
                            long revisionNumber = commitInfo.getRevisionNumber();
                            Date date = commitInfo.getDate();
                            if(date != null){
                                Locale locale = Locale.getDefault();
                                if(locale == null){
                                    locale = Locale.US;
                                }
                                SimpleDateFormat df = new SimpleDateFormat("yy-MM-dd aHH:mm", locale);
                                String datestr = df.format(date);
                                vcsMessage = " " + revisionNumber + " " + datestr + " " + author;
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
}
