package org.jenkinsci.plugins.feishuwatcher;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.logging.Logger;


public class WatcherJobProperty extends JobProperty<Job<?, ?>> {
    private static final Logger LOGGER = Logger.getLogger(WatcherJobProperty.class.getName());

    private final String webhookurl;
    private final String mention;

    @DataBoundConstructor
    public WatcherJobProperty(final String webhookurl, final String mention) {
        this.webhookurl = webhookurl;
        this.mention = mention;
    }

    public String getWebhookurl() {
        return webhookurl;
    }

    public String getMention() {
        return mention;
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

            if (addresses == null || addresses.isEmpty())
                return null;

            return new WatcherJobProperty(addresses, mention);
        }

        public FormValidation doCheckWebhookurl(@QueryParameter String value) {
            LOGGER.info("doCheckWebhookurl: " + value);
            return FormValidation.ok();
        }

        public FormValidation doCheckMention(@QueryParameter String value) {
            LOGGER.info("doCheckMention: " + value);
            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return "Feishu when Job configuration changes";
        }
    }
}
