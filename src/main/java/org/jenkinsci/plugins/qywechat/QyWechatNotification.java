package org.jenkinsci.plugins.qywechat;

import com.arronlong.httpclientutil.exception.HttpProcessException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.qywechat.dto.BuildBeginInfo;
import org.jenkinsci.plugins.qywechat.dto.BuildMentionedInfo;
import org.jenkinsci.plugins.qywechat.dto.BuildOverInfo;
import org.jenkinsci.plugins.qywechat.model.NotificationConfig;
import org.jenkinsci.plugins.qywechat.utils.BuildUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * 企业微信构建通知
 *
 * @author jiaju
 */
public class QyWechatNotification extends Notifier implements SimpleBuildStep {

    private String webhookUrl;

    private String mentionedId;

    private String mentionedMobile;

    private boolean failNotify;

    private String projectName;

    /**
     * 此参数使用流水脚本时有效
     */
    private List<ChangeLogSet<?>> changeLogSets;

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    @DataBoundConstructor
    public QyWechatNotification() {
    }

//    @DataBoundConstructor
//    public QyWechatNotification(String mentionedId, String mentionedMobile, String webhookUrl) {
//        this.webhookUrl = webhookUrl;
//        this.mentionedId = mentionedId;
//        this.mentionedMobile = mentionedMobile;
//    }

    //支持流水线脚本

    /**
     * 开始执行构建
     *
     * @param build
     * @param listener
     * @return
     * @implNote 如果在流水线脚本使用此插件, 预构建此步骤不会生效
     */
    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        EnvVars envVars;
        listener.getLogger().println("开始构建..企微推送 begin");
        try {
            envVars = build.getEnvironment(listener);
        } catch (Exception e) {
            listener.getLogger().println("读取环境变量异常" + e.getMessage());
            envVars = new EnvVars();
        }
        NotificationConfig config = getConfig(envVars, listener);
        if (StringUtils.isEmpty(config.webhookUrl)) {
            return true;
        }
        if (build.getProject().getFullDisplayName() != null) {
            this.projectName = build.getProject().getFullDisplayName();
        }
        BuildBeginInfo buildInfo = new BuildBeginInfo(this.projectName, build, config);
        listener.getLogger().println("start build project:" + this.projectName);
        String req = buildInfo.toJSONString();
        listener.getLogger().println("推送通知" + req);
        //执行推送
        push(listener.getLogger(), config.webhookUrl, req, config);
        listener.getLogger().println("开始构建..企微推送 end");
        return true;
    }

    /**
     * 构建结束
     *
     * @param run
     * @param workspace
     * @param launcher
     * @param listener
     * @throws InterruptedException
     * @throws IOException
     */
    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        NotificationConfig config = getConfig(run.getEnvironment(listener), listener);
        listener.getLogger().println("构建结束..企微推送 begin");
        if (StringUtils.isEmpty(config.webhookUrl)) {
            return;
        }
        Result result = run.getResult();
        //todo 设置当前项目名称  兼容 WorkflowRun 流水任务
        if (run instanceof AbstractBuild) {
            this.projectName = run.getParent().getFullDisplayName();
        }
        BuildOverInfo buildInfo = new BuildOverInfo(this.projectName, run, config);
        //构建开始通知
        if (run instanceof WorkflowRun) {
            WorkflowRun build = (WorkflowRun) run;
            //todo 这里如果有日志不往下执行,从pipeline 步骤控制
            if (!CollectionUtils.isEmpty(build.getChangeSets())) {
                String changeLog = BuildUtils.buildChangeLog(build.getChangeSets());
                buildInfo.setChangeLog(changeLog);
                listener.getLogger().println(String.format("修改记录:%s\n", changeLog));
                String req = buildInfo.toJSONString();
                push(listener.getLogger(), config.webhookUrl, req, config);
                return;
            }
        }
        String req = buildInfo.toJSONString();
        listener.getLogger().println("推送通知" + req);
        //推送结束通知
        push(listener.getLogger(), config.webhookUrl, req, config);
        //运行不成功
        if (result == null) {
            return;
        }
        listener.getLogger().println("项目运行结果[" + result + "]");
        //仅在失败的时候，才进行@
        if (!result.equals(Result.SUCCESS) || !config.failNotify) {
            //没有填写UserId和手机号码
            if (StringUtils.isEmpty(config.mentionedId) && StringUtils.isEmpty(config.mentionedMobile)) {
                return;
            }
            //构建@通知
            BuildMentionedInfo consoleInfo = new BuildMentionedInfo(run, config);
            req = consoleInfo.toJSONString();
            listener.getLogger().println("推送通知" + req);
            //执行推送
            push(listener.getLogger(), config.webhookUrl, req, config);
            listener.getLogger().println("构建结束..企微推送 end");
        }
    }

    /**
     * 推送消息
     *
     * @param logger
     * @param url
     * @param data
     * @param config
     */
    private void push(PrintStream logger, String url, String data, NotificationConfig config) {
        String[] urls;
        if (url.contains(",")) {
            urls = url.split(",");
        } else {
            urls = new String[]{url};
        }
        for (String u : urls) {
            try {
                String msg = NotificationUtil.push(u, data, config);
                logger.println("通知结果" + msg);
            } catch (HttpProcessException e) {
                logger.println("通知异常" + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    /**
     * 读取配置，将当前Job与全局配置整合
     *
     * @param envVars
     * @return
     */
    public NotificationConfig getConfig(EnvVars envVars, TaskListener listener) {
        NotificationConfig config = DESCRIPTOR.getUnsaveConfig();
        if (StringUtils.isNotEmpty(webhookUrl)) {
            config.webhookUrl = webhookUrl;
        }
        if (StringUtils.isNotEmpty(mentionedId)) {
            config.mentionedId = mentionedId;
        }
        if (StringUtils.isNotEmpty(mentionedMobile)) {
            config.mentionedMobile = mentionedMobile;
        }
        config.failNotify = failNotify;
        //使用环境变量
        if (config.webhookUrl.contains("$")) {
            String val = NotificationUtil.replaceMultipleEnvValue(config.webhookUrl, envVars);
            config.webhookUrl = val;
        }
        if (config.projectName.contains("$")) {
            String val = NotificationUtil.replaceMultipleEnvValue(config.projectName, envVars);
            config.projectName = val;
        }
        if (config.mentionedId.contains("$")) {
            String val = NotificationUtil.replaceMultipleEnvValue(config.mentionedId, envVars);
            config.mentionedId = val;
        }
        if (config.mentionedMobile.contains("$")) {
            String val = NotificationUtil.replaceMultipleEnvValue(config.mentionedMobile, envVars);
            config.mentionedMobile = val;
        }
//        StringBuffer stringBuffer = new StringBuffer();
//        for (Map.Entry<String, String> entry : envVars.entrySet()) {
//            stringBuffer.append("key:".concat(entry.getKey()).concat("val:").concat(entry.getValue()).concat("\n"));
//        }
//
//        listener.getLogger().println("环境参数:".concat(stringBuffer.toString()));
        return config;
    }

    /**
     * 下面为GetSet方法，当前Job保存时进行绑定
     **/

    @DataBoundSetter
    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @DataBoundSetter
    public void setMentionedId(String mentionedId) {
        this.mentionedId = mentionedId;
    }

    @DataBoundSetter
    public void setMentionedMobile(String mentionedMobile) {
        this.mentionedMobile = mentionedMobile;
    }

    @DataBoundSetter
    public void setFailNotify(boolean failNotify) {
        this.failNotify = failNotify;
    }


    public List<ChangeLogSet<?>> getChangeLogSets() {
        return changeLogSets;
    }

    @DataBoundSetter
    public void setChangeLogSets(List<ChangeLogSet<?>> changeLogSets) {
        this.changeLogSets = changeLogSets;
    }

    public String getProjectName() {
        return projectName;
    }

    @DataBoundSetter
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public String getMentionedId() {
        return mentionedId;
    }

    public String getMentionedMobile() {
        return mentionedMobile;
    }

    public boolean isFailNotify() {
        return failNotify;
    }
}

