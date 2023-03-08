package org.jenkinsci.plugins.feishuwatcher;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import hudson.slaves.OfflineCause;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class NodeAwailabilityListener extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(NodeAwailabilityListener.class.getName());

    private static final List<String> IGNORED_CLASSES = Arrays.asList(
            "org.jenkinsci.plugins.workflow.job.WorkflowRun"
    );

    private final FeishuWatcher feishu;
    private final String jenkinsRootUrl;

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public NodeAwailabilityListener() {
        this(new FeishuWatcher(Jenkins.get()), Jenkins.get().getRootUrl());
    }

    public NodeAwailabilityListener(final FeishuWatcher feishu, final String jenkinsRootUrl) {
        if (feishu == null) throw new IllegalArgumentException(
                "No feishu provided"
        );

        this.feishu = feishu;
        this.jenkinsRootUrl = jenkinsRootUrl;
    }

    private boolean isIgnoredRunClass(Run<?, ?> r) {
        String runClassName = r.getClass().getCanonicalName();
        return IGNORED_CLASSES.contains(runClassName);
    }

    @Override
    public void onFinalized(Run<?, ?> r) {
        if (isIgnoredRunClass(r)) {
            return;
        }

        Computer computer = computer(r);
        if (computer == null) {
            String msg = String.format("Unable to identify the slave of %s (%s)", r, r.getClass());
            LOGGER.log(Level.INFO, msg, new Exception());
            return;
        }

        if (!computer.isTemporarilyOffline()) return;

        User user = user(computer);
        if (user == null) return;

        if (!isIdle(computer)) return;

        final String subject = "Jenkins computer '" + computer.getDisplayName() + "' you have put offline is no longer occupied";
        getNotification().subject(subject).url(computer.getUrl()).initiator(user).send(r);
    }

    private @CheckForNull
    User user(Computer computer) {
        OfflineCause cause = computer.getOfflineCause();
        if (cause instanceof OfflineCause.UserCause) {
            return ((OfflineCause.UserCause) cause).getUser();
        }

        return null;
    }

    private @CheckForNull
    Computer computer(Run<?, ?> r) {
        if (r instanceof AbstractBuild) {
            Node node = ((AbstractBuild<?, ?>) r).getBuiltOn();
            if (node != null) {
                return node.toComputer();
            }
        }
        return null;
    }

    private boolean isIdle(Computer computer) {
        Thread current = Thread.currentThread();
        for (Executor e : computer.getExecutors()) {
            if (!e.isIdle() && e != current) return false;
        }

        for (Executor e : computer.getOneOffExecutors()) {
            if (!e.isIdle() && e != current) return false;
        }

        return true;
    }

    private Notification.Builder getNotification() {
        return new Notification.Builder(feishu, jenkinsRootUrl);
    }

    private static class Notification extends FeishuWatcherNotification {

        public Notification(Builder builder) {
            super(builder);
        }

        private static class Builder extends FeishuWatcherNotification.Builder {

            public Builder(final FeishuWatcher feishu, final String jenkinsRootUrl) {
                super(feishu, jenkinsRootUrl);
            }

            @Override
            public void send(final Object o) {
                new Notification(this).send();
            }
        }
    }
}
