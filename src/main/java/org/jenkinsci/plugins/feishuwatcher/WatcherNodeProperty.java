package org.jenkinsci.plugins.feishuwatcher;

import hudson.Extension;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.logging.Logger;


public class WatcherNodeProperty extends NodeProperty<Node> {
    private static final Logger LOGGER = Logger.getLogger(WatcherNodeProperty.class.getName());

    private final String webhookurl;
    private final String mention;

    @DataBoundConstructor
    public WatcherNodeProperty(final String webhookurl, final String mention) {
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
    public static class DescriptorImpl extends NodePropertyDescriptor {

        @Override
        public boolean isApplicable(Class<? extends Node> nodeType) {
            return true;
        }

        @Override
        public NodeProperty<?> newInstance(final StaplerRequest req, final JSONObject formData) throws FormException {
            final String webhookurl = formData.getString("webhookurl");
            final String mention = formData.getString("mention");
            LOGGER.info("newInstance: webhookurl=" + webhookurl);
            LOGGER.info("newInstance: mention=" + mention);

            assert webhookurl != null;
            assert mention != null;

            if (webhookurl.isEmpty() && mention.isEmpty()) return null;

            return new WatcherNodeProperty(webhookurl, mention);
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
            return "Feishu when Node online status changes";
        }
    }
}
