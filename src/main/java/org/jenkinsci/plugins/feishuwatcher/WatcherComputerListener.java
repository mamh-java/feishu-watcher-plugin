package org.jenkinsci.plugins.feishuwatcher;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.OfflineCause;
import hudson.util.DescribableList;
import jenkins.model.Jenkins;

import java.util.logging.Logger;


@Extension
public class WatcherComputerListener extends ComputerListener {
    private static final Logger LOGGER = Logger.getLogger(WatcherComputerListener.class.getName());

    private final FeishuWatcher feishu;
    private final String jenkinsRootUrl;

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public WatcherComputerListener() {
        this(new FeishuWatcher(Jenkins.get()), Jenkins.get().getRootUrl());
    }

    public WatcherComputerListener(final FeishuWatcher feishu, final String jenkinsRootUrl) {
        if (feishu == null) throw new IllegalArgumentException(
                "No feishu provided"
        );

        this.feishu = feishu;
        this.jenkinsRootUrl = jenkinsRootUrl;
    }

    @Override
    public void onOffline(final Computer c) {
        getNotification().online(false).subject("marked offline").send(c);
    }

    @Override
    public void onOffline(final Computer c, final OfflineCause cause) {
        if (cause == null) {
            onOffline(c);
            return;
        }

        getNotification().online(false).subject("marked offline").body(cause.toString()).send(c);
    }

    @Override
    public void onOnline(final Computer c, final TaskListener listener) {
        getNotification().online(true).subject("marked online").send(c);
    }

    @Override
    public void onTemporarilyOffline(final Computer c, final OfflineCause cause) {
        String causeString = "";
        if (cause != null) {
            causeString = cause.toString();
        }
        getNotification().online(false).subject("marked temporarily offline").body(causeString).send(c);
    }

    @Override
    public void onTemporarilyOnline(final Computer c) {
        getNotification().online(true).subject("marked online (was temporarily offline)").send(c);
    }

    private Notification.Builder getNotification() {
        return new Notification.Builder(feishu, jenkinsRootUrl);
    }

    private static class Notification extends FeishuWatcherNotification {

        public Notification(final Builder builder) {
            super(builder);
        }

        @Override
        protected String getSubject() {
            return String.format("Computer %s %s", getName(), super.getSubject());
        }

        private static class Builder extends FeishuWatcherNotification.Builder {

            private boolean online;

            public Builder(final FeishuWatcher feishu, final String jenkinsRootUrl) {
                super(feishu, jenkinsRootUrl);
            }

            public Builder online(final boolean online) {
                this.online = online;
                return this;
            }

            @Override
            public void send(final Object o) {
                final Computer computer = (Computer) o;
                final WatcherNodeProperty property = getWatcherNodeProperty(computer);

                if (property == null) { //只有这里获取 到的 prop 不是null 才去发送通知
                    return;
                }
                if (!online) {
                    LOGGER.info("online status is: " + online); // 只有online是false的时候才去 @ 人
                    this.recipients(property.getMention());
                }
                this.webhookurl(property.getWebhookurl());

                this.url(computer.getUrl());
                this.name(computer.getDisplayName());

                new Notification(this).send();
            }

            private static WatcherNodeProperty getWatcherNodeProperty(final Computer computer) {
                final Node node = computer.getNode();
                if (node == null) return null;
                final DescribableList<NodeProperty<?>, NodePropertyDescriptor> properties = node.getNodeProperties();
                return properties.get(WatcherNodeProperty.class);
            }
        }
    }
}
