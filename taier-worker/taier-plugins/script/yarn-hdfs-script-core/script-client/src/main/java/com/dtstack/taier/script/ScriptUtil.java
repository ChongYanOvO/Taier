package com.dtstack.taier.script;

import com.dtstack.taier.pluginapi.JobClient;
import com.dtstack.taier.pluginapi.util.DtStringUtil;
import com.google.common.collect.Lists;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.BASE64Decoder;

import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class ScriptUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ScriptClient.class);

    private static final BASE64Decoder DECODER = new BASE64Decoder();

    /**
     * 组装运行时参数
     *
     * @param jobClient
     * @param dtconf
     * @return
     * @throws IOException
     */
    public static ScriptConfiguration buildScriptConf(JobClient jobClient, ScriptConfiguration dtconf) throws IOException {
        String exeArgs = jobClient.getClassArgs();
        List<String> args = DtStringUtil.splitIngoreBlank(exeArgs);
        ScriptConfiguration execDtconf = new ScriptConfiguration(false);

        for (Map.Entry<String, String> param : dtconf) {
            String key = param.getKey();
            String value = param.getValue();
            if (key.contains("memory")) {
                value = String.valueOf(getNormalizedMem(param.getValue()));
            }
            execDtconf.set(key,value);
        }

        // exeArgs, 来自 com.dtstack.taier.develop.service.develop.saver.ScriptTaskSaver.populateExeArgs
        // 上下两个为一组 k/v
        for (int i = 0; i < args.size() - 1; i += 2) {
            // 替换前缀「--」为 「script」、替换「-」为「.」，比如「--app-type」为 「script.app.type」
            String key = args.get(i).replace("--", "script.").replace("-", ".");
            String values = args.get(i + 1);
            if ("script.launch.cmd".equals(args.get(i)) || "script.cmd.opts".equals(args.get(i))) {
                dtconf.set(key, new String(DECODER.decodeBuffer(args.get(i + 1)), "UTF-8"));
                continue;
            }
            if (ScriptConfiguration.SCRIPT_SHIP_FILES.equals(args.get(i)) && StringUtils.isNotBlank(dtconf.get(ScriptConfiguration.SCRIPT_SHIP_FILES))) {
                dtconf.set(key, args.get(i + 1) + "," + dtconf.get(ScriptConfiguration.SCRIPT_SHIP_FILES));
                continue;
            }
            execDtconf.set(key, values);
        }

        // taskParams，任务新增时设置
        Properties confProperties = jobClient.getConfProperties();
        confProperties.stringPropertyNames().stream()
        .map(String::trim)
        .forEach(
            key -> {
                String value = confProperties.getProperty(key).trim();
                if (key.contains("memory")) {
                    value = String.valueOf(getNormalizedMem(value));
                }
                if (key.contains("priority")) {
                    key = "priority";
                    value = String.valueOf(jobClient.getPriority()).trim();
                }
                execDtconf.set(key, value);
            });
        return execDtconf;
    }

    public static ScriptConfiguration initScriptConfiguration(Properties prop, ScriptConfiguration dtconf) {
        List<String> removeConf = Lists.newArrayList("sftpConf", "hiveConf", "hadoopConf", "yarnConf");

        dtconf.setBoolean(ScriptConfiguration.SCRIPT_USER_CLASSPATH_FIRST, ScriptConfiguration.DEFAULT_SCRIPT_USER_CLASSPATH_FIRST);
        dtconf.set(ScriptConfiguration.APP_MAX_ATTEMPTS, String.valueOf(ScriptConfiguration.DEFAULT_APP_MAX_ATTEMPTS));
        dtconf.set(ScriptConfiguration.SCRIPT_LOGLEVEL, ScriptConfiguration.DEFAULT_SCRIPT_LOGLEVEL);
        String queue = prop.getProperty(ScriptConfiguration.APP_QUEUE);
        if (StringUtils.isNotBlank(queue)) {
            LOG.info("curr queue is {}", queue);
            dtconf.set(ScriptConfiguration.APP_QUEUE, queue);
        }
        Enumeration enumeration = prop.propertyNames();
        while (enumeration.hasMoreElements()) {
            String key = (String) enumeration.nextElement();
            if (removeConf.contains(key)) {
                continue;
            }
            Object value = prop.get(key);
            if (value instanceof String) {
                if (key.contains("memory")){
                    value = String.valueOf(getNormalizedMem((String)value));
                }
                dtconf.set(key, (String) value);
            } else if (value instanceof Integer) {
                dtconf.setInt(key, (Integer) value);
            } else if (value instanceof Float) {
                dtconf.setFloat(key, (Float) value);
            } else if (value instanceof Double) {
                dtconf.setDouble(key, (Double) value);
            } else if (value instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) value;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    key = entry.getKey();
                    value = MapUtils.getString(map, entry.getKey());
                    dtconf.set(key, (String) value);
                }
            } else {
                dtconf.set(key, value.toString());
            }
        }
        return dtconf;
    }

    private static int getNormalizedMem(String rawMem) {
        if (rawMem.endsWith("G") || rawMem.endsWith("g")) {
            return Integer.parseInt(rawMem.substring(0, rawMem.length() - 1)) * 1024;
        } else if (rawMem.endsWith("M") || rawMem.endsWith("m")) {
            return Integer.parseInt(rawMem.substring(0, rawMem.length() - 1));
        } else {
            return Integer.parseInt(rawMem);
        }
    }
}