package org.jenkinsci.plugins.feishuwatcher.jobConfigHistory;

import hudson.model.Job;
import hudson.plugins.jobConfigHistory.ConfigInfo;
import hudson.plugins.jobConfigHistory.JobConfigHistory;
import hudson.plugins.jobConfigHistory.JobConfigHistoryProjectAction;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;


public class ConfigHistory {

    private final JobConfigHistory plugin;

    public ConfigHistory(final JobConfigHistory plugin) {
        this.plugin = plugin;
    }

    public @CheckForNull
    String lastChangeDiffUrl(final @Nonnull Job<?, ?> job) {
        if (plugin == null) return null;

        final List<ConfigInfo> configs = storedConfigurations(job);
        if (configs == null || configs.size() < 2) return null;

        return String.format(
                "%sjobConfigHistory/showDiffFiles?timestamp1=%s&timestamp2=%s",
                job.getShortUrl(), configs.get(1).getDate(), configs.get(0).getDate()
        );
    }

    private @CheckForNull
    List<ConfigInfo> storedConfigurations(final Job<?, ?> job) {

        final JobConfigHistoryProjectAction action = job.getAction(JobConfigHistoryProjectAction.class);

        if (action == null) return null;

        try {

            return action.getJobConfigs();
        } catch (IOException ex) {

            ex.printStackTrace();
            return null;
        }
    }
}
