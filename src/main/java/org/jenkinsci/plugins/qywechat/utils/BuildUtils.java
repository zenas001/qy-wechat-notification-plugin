package org.jenkinsci.plugins.qywechat.utils;

import hudson.scm.ChangeLogSet;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author : zhaoqixiang
 * @apiNote :构建信息生成工具
 * @date : 2021年06月03日
 */
public class BuildUtils {
    private static SimpleDateFormat format;

    static {
        format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 构建gitchange 日志
     */
    public static String buildChangeLog(List<ChangeLogSet<?>> changeSets) {
        StringBuffer stringBuffer = new StringBuffer();
        for (ChangeLogSet<?> changeLogSet : changeSets) {
            for (Object item : changeLogSet.getItems()) {
                if (item instanceof ChangeLogSet.Entry) {
                    ChangeLogSet.Entry entry = (ChangeLogSet.Entry) item;
                    stringBuffer.append("【提交描述】:" + entry.getMsg() + "【提交人】:" + entry.getAuthor() + "【提交时间】：" + format.format(new Date(entry.getTimestamp())));
                    stringBuffer.append("\n");
                }
            }
        }
        if (stringBuffer.length() > 1) {
            stringBuffer.delete(stringBuffer.length() - 1, stringBuffer.length());
        }
        return stringBuffer.toString();
    }
}
