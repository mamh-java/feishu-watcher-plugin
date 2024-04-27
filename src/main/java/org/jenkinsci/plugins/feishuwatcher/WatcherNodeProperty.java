package org.jenkinsci.plugins.feishuwatcher;

import hudson.Extension;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.logging.Logger;


public class WatcherNodeProperty extends NodeProperty<Node> {
    private static final Logger LOGGER = Logger.getLogger(WatcherNodeProperty.class.getName());

    private final String webhookurl;
    private final String mention;
    private final boolean post;

    @DataBoundConstructor
    public WatcherNodeProperty(final String webhookurl, final String mention, final boolean post) {
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
    public static class DescriptorImpl extends NodePropertyDescriptor {

        @Override
        public boolean isApplicable(Class<? extends Node> nodeType) {
            return true;
        }

        @Override
        public NodeProperty<?> newInstance(final StaplerRequest req, final JSONObject formData) throws FormException {
            final String webhookurl = formData.getString("webhookurl");
            final String mention = formData.getString("mention");
            final boolean post = formData.getBoolean("post");

            assert webhookurl != null;
            assert mention != null;

            if (webhookurl.isEmpty() && mention.isEmpty()) return null;

            return new WatcherNodeProperty(webhookurl, mention, post);
        }

        public FormValidation doCheckWebhookurl(@QueryParameter String value) {
            if(StringUtils.isEmpty(value)){
                return FormValidation.error("webhook url is empty");
            }
            if(!value.startsWith("http")){
                return FormValidation.error("webhook url not http/https");
            }
            if(!value.contains("/open-apis/bot/v2/hook")){
                return FormValidation.error("webhook api should be open-apis/bot/v2/hook");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckMention(@QueryParameter String value) {
            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return "Feishu when Node online status changes";
        }
    }
}
