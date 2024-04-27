package org.jenkinsci.plugins.feishuwatcher;

import hudson.model.User;

import javax.annotation.Nonnull;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;


public abstract class FeishuWatcherNotification {

    private static final Logger LOGGER = Logger.getLogger(FeishuWatcherNotification.class.getName());

    private static final String WATCHER_PLUGIN = "feishu-watcher-plugin: ";

    final private String subject;
    final private String body;
    final private String recipients;

    final private String url;
    final private String webhookurl;
    final private String resourceName;
    final private User initiator;

    final private String jenkinsRootUrl;

    final protected FeishuWatcher feishu;

    public FeishuWatcherNotification(final Builder builder) {
        this.subject = builder.subject;
        this.body = builder.body;
        this.recipients = builder.recipients;
        this.webhookurl = builder.webhookurl;
        this.url = builder.url;
        this.resourceName = builder.resourceName;
        this.initiator = builder.initiator;
        this.jenkinsRootUrl = builder.jenkinsRootUrl;
        this.feishu = builder.feishu;
    }

    protected String getSubject() {
        return subject;
    }

    protected String getBody() {
        return body;
    }

    public String getRecipients() {
        return recipients;
    }

    public String getWebhookurl() {
        return webhookurl;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return resourceName;
    }

    public String getArtefactUrl() {
        return jenkinsRootUrl + this.getUrl();
    }

    public User getInitiator() {
        return initiator;
    }

    protected boolean shouldNotify() {
        return recipients != null;
    }

    public final String getMailSubject() {
        return WATCHER_PLUGIN + this.getSubject();
    }

    public final String getMailBody() {
        final StringBuilder body = new StringBuilder();
        for (final Map.Entry<String, String> pair : pairs().entrySet()) {
            body.append(pair(pair.getKey(), pair.getValue()));
        }

        return body.append("\n\n").append(this.getBody()).toString();
    }

    protected @Nonnull
    Map<String, String> pairs() {
        final Map<String, String> pairs = new HashMap<>(3);
        pairs.put("发现时间", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        pairs.put("发起者是", this.getInitiator().getId());
        pairs.put("Url", "[" + this.getArtefactUrl() + "](" + this.getArtefactUrl() + ")");
        return pairs;
    }

    private String pair(final String key, final String value) {
        return String.format("%s: %s%n", key, value);
    }

    public final void send() {
        try {
            final String msg = feishu.send(this);
            if (msg != null) {
                LOGGER.info("notified: " + this.getSubject());
            }
        } catch (AddressException ex) {
            LOGGER.info("unable to parse address");
        } catch (MessagingException ex) {
            LOGGER.info("unable to notify");
        }
    }

    public static abstract class Builder {

        final protected FeishuWatcher feishu;
        final private String jenkinsRootUrl;

        private String subject = "";
        private String body = "";
        private String recipients; //收件人

        private String url = "";
        private String webhookurl = "";
        private String resourceName = "";
        private User initiator;

        public Builder(final FeishuWatcher feishu, final String jenkinsRootUrl) {
            this.feishu = feishu;
            this.initiator = feishu.getDefaultInitiator();
            this.jenkinsRootUrl = jenkinsRootUrl == null ? "/" : jenkinsRootUrl;
        }

        public Builder subject(final String subject) {
            this.subject = subject;
            return this;
        }

        public Builder body(final String body) {
            this.body = body;
            return this;
        }

        public Builder recipients(final String recipients) {
            this.recipients = recipients;
            return this;
        }

        protected Builder url(final String url) {
            this.url = url;
            return this;
        }

        protected Builder webhookurl(final String webhookurl) {
            this.webhookurl = webhookurl;
            return this;
        }

        protected Builder name(final String name) {
            this.resourceName = name;
            return this;
        }

        protected Builder initiator(final User initiator) {
            this.initiator = initiator;
            return this;
        }

        abstract public void send(final Object object);
    }
}
