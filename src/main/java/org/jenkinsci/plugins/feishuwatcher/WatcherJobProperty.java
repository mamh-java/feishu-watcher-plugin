package org.jenkinsci.plugins.feishuwatcher;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.logging.Logger;


public class WatcherJobProperty extends JobProperty<Job<?, ?>> {
    private static final Logger LOGGER = Logger.getLogger(WatcherJobProperty.class.getName());

    private final String webhookurl;
    private final String mention;
    private final boolean post;

    @DataBoundConstructor
    public WatcherJobProperty(final String webhookurl, final String mention, final boolean post) {
        this.webhookurl = webhookurl;
        this.mention = mention;
        this.post = post;
    }

    public String getWebhookurl() {
        return webhookurl;
    }

    public String getMention() {
        return mention;
    }

    public boolean isPost() {
        return post;
    }

    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {

        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends Job> jobType) {
            return true;
        }

        @Override
        public JobProperty<?> newInstance(final StaplerRequest req, final JSONObject formData) throws FormException {
            final JSONObject watcherData = formData.getJSONObject("watcherEnabled");
            if (watcherData.isNullObject())
                return null;

            final String addresses = watcherData.getString("webhookurl");
            final String mention = watcherData.getString("mention");
            final boolean post = watcherData.getBoolean("post");

            if (addresses == null || addresses.isEmpty())
                return null;

            return new WatcherJobProperty(addresses, mention, post);
        }

        public FormValidation doCheckWebhookurl(@QueryParameter String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error("webhook url is empty");
            }
            if (!value.startsWith("http")) {
                return FormValidation.error("webhook url not http/https");
            }
            if (!value.contains("/open-apis/bot/v2/hook")) {
                return FormValidation.error("webhook api should be open-apis/bot/v2/hook");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckMention(@QueryParameter String value) {
            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return "Feishu when Job configuration changes";
        }
    }
}
