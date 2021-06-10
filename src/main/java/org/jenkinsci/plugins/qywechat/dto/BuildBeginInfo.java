package org.jenkinsci.plugins.qywechat.dto;

import hudson.model.AbstractBuild;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.scm.ChangeLogSet;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.qywechat.NotificationUtil;
import org.jenkinsci.plugins.qywechat.model.NotificationConfig;
import org.jenkinsci.plugins.qywechat.utils.BuildUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开始构建的通知信息
 *
 * @author jiaju
 * @implNote 更新：增加修改记录输出
 */
public class BuildBeginInfo {
    private static Logger logger = LoggerFactory.getLogger(BuildBeginInfo.class);

    /**
     * 请求参数
     */
    private Map params = new HashMap<String, Object>();

//    private SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    /**
     * 预计时间，毫秒
     */
    private Long durationTime = 0L;

    /**
     * 本次构建控制台地址
     */
    private String consoleUrl;

    /**
     * 工程名称
     */
    private String projectName;

    /**
     * 环境名称
     */
    private String topicName = "";

    /**
     * 本次修改记录
     */
    private String changeLog = "";

    public BuildBeginInfo(String projectName, AbstractBuild<?, ?> build, NotificationConfig config) {
        //获取请求参数
        List<ParametersAction> parameterList = build.getActions(ParametersAction.class);
        if (parameterList != null && parameterList.size() > 0) {
            for (ParametersAction p : parameterList) {
                for (ParameterValue pv : p.getParameters()) {
                    this.params.put(pv.getName(), pv.getValue());
                }
            }
        }
        //预计时间
        if (build.getProject().getEstimatedDuration() > 0) {
            this.durationTime = build.getProject().getEstimatedDuration();
        }
        //控制台地址
        StringBuilder urlBuilder = new StringBuilder();
        String jenkinsUrl = NotificationUtil.getJenkinsUrl();
        if (StringUtils.isNotEmpty(jenkinsUrl)) {
            String buildUrl = build.getUrl();
            urlBuilder.append(jenkinsUrl);
            if (!jenkinsUrl.endsWith("/")) {
                urlBuilder.append("/");
            }
            urlBuilder.append(buildUrl);
            if (!buildUrl.endsWith("/")) {
                urlBuilder.append("/");
            }
            urlBuilder.append("console");
        }
        this.consoleUrl = urlBuilder.toString();
        //工程名称
        this.projectName = projectName;
        //环境名称
        if (config.topicName != null) {
            topicName = config.topicName;
        }
        //修改记录
        List<ChangeLogSet<?>> changeSets = build.getChangeSets();
        String changeLog = BuildUtils.buildChangeLog(changeSets);
        this.changeLog = changeLog;
    }

    public void setChangeLog(String changeLog) {
        this.changeLog = changeLog;
    }

    public BuildBeginInfo(String projectName, WorkflowRun workflowRun, NotificationConfig config) {
        //获取请求参数
        List<ParametersAction> parameterList = workflowRun.getActions(ParametersAction.class);
        if (parameterList != null && parameterList.size() > 0) {
            for (ParametersAction p : parameterList) {
                for (ParameterValue pv : p.getParameters()) {
                    this.params.put(pv.getName(), pv.getValue());
                }
            }
        }
        //预计时间
        if (workflowRun.getParent().getEstimatedDuration() > 0) {
            this.durationTime = workflowRun.getParent().getEstimatedDuration();
        }
        //控制台地址
        StringBuilder urlBuilder = new StringBuilder();
        String jenkinsUrl = NotificationUtil.getJenkinsUrl();
        if (StringUtils.isNotEmpty(jenkinsUrl)) {
            String buildUrl = workflowRun.getUrl();
            urlBuilder.append(jenkinsUrl);
            if (!jenkinsUrl.endsWith("/")) {
                urlBuilder.append("/");
            }
            urlBuilder.append(buildUrl);
            if (!buildUrl.endsWith("/")) {
                urlBuilder.append("/");
            }
            urlBuilder.append("console");
        }
        this.consoleUrl = urlBuilder.toString();
        //工程名称
        this.projectName = projectName;
        //环境名称
        if (config.topicName != null) {
            topicName = config.topicName;
        }
        //修改记录
        List<ChangeLogSet<?>> changeSets = workflowRun.getChangeSets();
        String changeLog = BuildUtils.buildChangeLog(changeSets);
        this.changeLog = changeLog;
    }

    public String toJSONString() {
        //参数组装
        StringBuffer paramBuffer = new StringBuffer();
        params.forEach((key, val) -> {
            paramBuffer.append(key);
            paramBuffer.append("=");
            paramBuffer.append(val);
            paramBuffer.append(", ");
        });
        if (paramBuffer.length() == 0) {
            paramBuffer.append("无");
        } else {
            paramBuffer.deleteCharAt(paramBuffer.length() - 2);
        }

        //耗时预计
        String durationTimeStr = "无";
        if (durationTime > 0) {
            Long l = durationTime / (1000 * 60);
            durationTimeStr = l + "分钟";
        }

        //组装内容
        StringBuilder content = new StringBuilder();
        if (StringUtils.isNotEmpty(topicName)) {
            content.append(this.topicName);
        }
        content.append("<font color=\"info\">【" + this.projectName + "】</font>开始构建\n");
        content.append(" >构建参数：<font color=\"comment\">" + paramBuffer.toString() + "</font>\n");
        content.append(" >预计用时：<font color=\"comment\">" + durationTimeStr + "</font>\n");
        content.append(" >本次更新内容：<font color=\"comment\"></font>\n");
        content.append("<font color=\"comment\">" + this.changeLog + "</font>\n");
        if (StringUtils.isNotEmpty(this.consoleUrl)) {
            content.append(" >[查看控制台](" + this.consoleUrl + ")");
        }

        Map markdown = new HashMap<String, Object>();
        markdown.put("content", content.toString());

        Map data = new HashMap<String, Object>();
        data.put("msgtype", "markdown");
        data.put("markdown", markdown);

        String req = JSONObject.fromObject(data).toString();
        return req;
    }


//    /**
//     * 构建gitchange 日志
//     */
//    public String buildChangeLog(List<ChangeLogSet<?>> changeSets) {
//        StringBuffer stringBuffer = new StringBuffer();
//        for (ChangeLogSet<?> changeLogSet : changeSets) {
//            for (Object item : changeLogSet.getItems()) {
//                if (item instanceof ChangeLogSet.Entry) {
//                    ChangeLogSet.Entry entry = (ChangeLogSet.Entry) item;
//                    stringBuffer.append("【提交描述】:" + entry.getMsg() + "【提交人】:" + entry.getAuthor() + "【提交时间】：" + format.format(new Date(entry.getTimestamp())));
//                    stringBuffer.append("\n");
//                }
//            }
//        }
//        if (stringBuffer.length() > 1) {
//            stringBuffer.delete(stringBuffer.length() - 1, stringBuffer.length());
//        }
//        return stringBuffer.toString();
//    }
}
