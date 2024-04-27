package org.jenkinsci.plugins.feishuwatcher;

import com.arronlong.httpclientutil.HttpClientUtil;
import com.arronlong.httpclientutil.common.HttpConfig;
import com.arronlong.httpclientutil.exception.HttpProcessException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Plugin;
import hudson.model.User;
import hudson.plugins.jobConfigHistory.JobConfigHistory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.jenkinsci.plugins.feishuwatcher.jobConfigHistory.ConfigHistory;
import com.google.common.base.Splitter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.net.ssl.SSLContext;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

public class FeishuWatcher {
    private static final Logger LOGGER = Logger.getLogger(FeishuWatcher.class.getName());

    private final @Nonnull
    Jenkins jenkins;
    private final @Nonnull
    ConfigHistory configHistory;

    public FeishuWatcher(final @Nonnull Jenkins jenkins) {
        this.jenkins = jenkins;
        this.configHistory = new ConfigHistory((JobConfigHistory) plugin("jobConfigHistory"));
    }

    @Nonnull
    User getDefaultInitiator() {
        final User current = User.current();
        return current != null ? current : User.getUnknown();
    }

    @CheckForNull
    Plugin plugin(final String plugin) {
        return jenkins.getPlugin(plugin);
    }

    @Nonnull
    URL absoluteUrl(final @Nonnull String url) {
        try {
            return new URL(jenkins.getRootUrl() + url);
        } catch (MalformedURLException ex) {
            throw new AssertionError(ex);
        }
    }

    @Nonnull
    ConfigHistory configHistory() {
        return configHistory;
    }


    public String send(final FeishuWatcherNotification notification) throws MessagingException, AddressException {
        //if (!notification.shouldNotify()) return null; 这里判断 收件人 是否是空了，是空就不通知了。
        String webhookurl = notification.getWebhookurl();

        String[] urls;
        if (webhookurl.contains(",")) {
            urls = webhookurl.split(",");
        } else {
            urls = new String[]{webhookurl};
        }
        if (urls.length == 0) {
            LOGGER.warning("webhookurl is null");
            return null;
        }

        String data = "";
        if(notification.isPost()){
             data = forPost(notification);
        }else{
            data = forText(notification);
        }
        LOGGER.info("will send msg: " + data);

        for (String u : urls) {
            try {
                String msg = push(u, data);
                LOGGER.info("send msg result" + msg);
            } catch (HttpProcessException | KeyManagementException | NoSuchAlgorithmException e) {
                LOGGER.info("send msg result" + e.getMessage());
                e.printStackTrace();
            }
        }
        return "";
    }

    /**
     * 发送文本消息
     * https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot#756b882f
     * {
     *     "msg_type": "text",
     *     "content": {
     *         "text": "<at user_id=\"ou_xxx\">Tom</at> 新更新提醒"
     *     }
     * }
     * @param notification
     * @return
     */
    private String forText(final FeishuWatcherNotification notification) {
        //组装内容
        String mention = notification.getRecipients();
        List<String> mentionedList = getMentionedList(mention);
        StringBuilder content = new StringBuilder();
        content.append(notification.getMailSubject());
        content.append("\n");
        content.append("\n");
        String body = notification.getBody();
        if(StringUtils.isNotEmpty(body)){
            content.append("说明: \n" + body+ "\n\n");
        }
        content.append("发现时间: ");
        content.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        content.append("\n");

        content.append("发起者是: ");
        content.append(notification.getInitiator().getId());
        content.append("\n");
        content.append("链接: ");
        content.append(notification.getArtefactUrl());
        content.append("\n");
        for (String mentioned : mentionedList) {
            if (mentioned.equals("@all")) {
                content.append("<at user_id=\"all\">所有人</at>\n");
            } else {
                content.append("<at user_id=\"" + mentioned + "\">" + mentioned + "</at>\n");
            }
        }
        MessageTextContent mtc = new MessageTextContent(content.toString());
        MessageText message = new MessageText("text", mtc);

        ObjectMapper mapper = new ObjectMapper();
        String req = "";
        try {
            req = mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return req;
    }

    /**
     * 发送富文本消息
     * https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot#f62e72d5
     * {
     *     "msg_type": "post",
     *     "content": {
     *         "post": {
     *             "zh_cn": {
     *                 "title": "项目更新通知",
     *                 "content": [
     *                     [
     *                         {
     *                             "tag": "text",
     *                             "text": "项目有更新: "
     *                         },
     *                         {
     *                             "tag": "a",
     *                             "text": "请查看",
     *                             "href": "http://www.example.com/"
     *                         },
     *                         {
     *                             "tag": "at",
     *                             "user_id": "ou_18eac8********17ad4f02e8bbbb"
     *                         }
     *                     ]
     *                 ]
     *             }
     *         }
     *     }
     * }
     * @param notification
     * @return
     */
    private String forPost(final FeishuWatcherNotification notification) {
        //组装内容
        String mention = notification.getRecipients();
        List<String> mentionedList = getMentionedList(mention);
        List<String> mobileList = getMobileList(mention);

        String subject = notification.getMailSubject();

        List<MessageItem> list = new ArrayList<>();
        list.add(new MessageItem("text", "\n", null, null));
        String body = notification.getBody();
        if(StringUtils.isNotEmpty(body)){
            list.add(new MessageItem("text", "说明: \n" + body+ "\n", null, null));
        }
        list.add(new MessageItem("text", "发现时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n", null, null));
        list.add(new MessageItem("text", "发起者是: " + notification.getInitiator().getId() + "\n", null, null));
        list.add(new MessageItem("text", "链接:  ", null, null));
        list.add(new MessageItem("a", "请点这里", notification.getArtefactUrl(), null));
        list.add(new MessageItem("text", "\n", null, null));
        for (String s : mentionedList) {
            list.add(new MessageItem("at", null, null, s));
        }
        for (String s : mobileList) {
            list.add(new MessageItem("at", null, null, s));
        }
        List<List<MessageItem>> conttentList = new ArrayList<>();
        conttentList.add(list);

        MessageZhCn zhcn = new MessageZhCn(subject, conttentList);
        MessagePost post = new MessagePost(zhcn);
        MessageContent content = new MessageContent(post);
        Message message = new Message("post", content);

        ObjectMapper mapper = new ObjectMapper();

        String req = "";
        try {
            req = mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return req;
    }

    private List<String> getMentionedList(String mention) { // # 用户 open_id  列表
        List<String> list = new ArrayList<>();
        if (StringUtils.isNotEmpty(mention)) {
            Iterable<String> iterable = Splitter.on(',').omitEmptyStrings().trimResults().split(mention);
            for (String result : iterable) {
                if (result.startsWith("ou") || result.contains("all")) {
                    list.add(result);
                }
            }
        }
        return list;
    }

    private List<String> getMobileList(String mention) { // # 用户 mobile 列表
        List<String> list = new ArrayList<>();
        if (StringUtils.isNotEmpty(mention)) {
            Iterable<String> iterable = Splitter.on(',').omitEmptyStrings().trimResults().split(mention);
            for (String result : iterable) {
                if (!result.startsWith("ou") && !result.contains("all")) {
                    list.add(result);
                }
            }
        }
        return list;
    }

    private static String push(String url, String data) throws HttpProcessException, KeyManagementException, NoSuchAlgorithmException {
        HttpConfig httpConfig;
        HttpClient httpClient;
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        if (url.startsWith("https")) {
            SSLContext sslContext = SSLContexts.custom().build();
            SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    new String[]{"TLSv1", "TLSv1.1", "TLSv1.2"},
                    null,
                    NoopHostnameVerifier.INSTANCE
            );
            httpClientBuilder.setSSLSocketFactory(sslConnectionSocketFactory);
        }

        httpClient = httpClientBuilder.build();
        //普通请求
        httpConfig = HttpConfig.custom().client(httpClient).url(url).json(data).encoding("utf-8");

        String result = HttpClientUtil.post(httpConfig);
        return result;
    }

    private static class MessageText {
        @JsonProperty("msg_type")
        public String msgType;
        @JsonProperty("content")
        public MessageTextContent content;

        public MessageText(String msgType, MessageTextContent content) {
            this.msgType = msgType;
            this.content = content;
        }
    }

    private static class MessageTextContent {
        @JsonProperty("text")
        public String text;

        public MessageTextContent(String text) {
            this.text = text;
        }
    }

    private static class Message {
        @JsonProperty("msg_type")
        public String msgType;
        @JsonProperty("content")
        public MessageContent content;

        public Message(String msgType, MessageContent content) {
            this.msgType = msgType;
            this.content = content;
        }
    }

    private static class MessageContent {
        @JsonProperty("post")
        public MessagePost post;

        public MessageContent(MessagePost post) {
            this.post = post;
        }
    }

    private static class MessagePost {
        @JsonProperty("zh_cn")
        public MessageZhCn zhcn;

        public MessagePost(MessageZhCn zhcn) {
            this.zhcn = zhcn;
        }
    }

    private static class MessageZhCn {
        @JsonProperty("title")
        public String title;
        @JsonProperty("content")
        public List<List<MessageItem>> content;

        public MessageZhCn(String title, List<List<MessageItem>> content) {
            this.title = title;
            this.content = content;
        }
    }

    private static class MessageItem {
        @JsonProperty("tag")
        public String tag;
        @JsonProperty("text")
        public String text;
        @JsonProperty("href")
        public String href;
        @JsonProperty("user_id")
        public String userId;

        public MessageItem(String tag, String text, String href, String userId) {
            this.tag = tag;
            this.text = text;
            this.href = href;
            this.userId = userId;
        }
    }

}
