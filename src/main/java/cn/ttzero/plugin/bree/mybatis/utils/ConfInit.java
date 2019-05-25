/*
 * Copyright (c) 2019, guanquan.wang@yandex.com All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.ttzero.plugin.bree.mybatis.utils;

import java.io.*;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import cn.ttzero.plugin.bree.mybatis.BreeMojo;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * 初始化所需文件
 * 首次执行自动copy所需文件<br/>
 * 如config.xml已存在则更新templates,table-config-1.1.dtd
 * Created by guanquan.wang at 2019-05-24 09:02
 */
public class ConfInit {
    private static final String CONFIG_PATH = "bree/config/";
    private static final String TEMPLATES_PATH = "bree/templates/";

    private static BreeMojo breeMojo;

    public static void configInit(BreeMojo breeMojo) throws MojoExecutionException {
        ConfInit.breeMojo = breeMojo;
        try {
            JarFile jarFile = new JarFile(URLDecoder.decode(ConfInit.class.getProtectionDomain().getCodeSource()
                .getLocation().getPath(), "UTF-8"));
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                // 复制模板
                if (StringUtils.startsWithIgnoreCase(jarEntry.getName(), TEMPLATES_PATH)) {
                    copyAndOverWriteFile(jarEntry.getName(),
                        new File(breeMojo.getTemplateDirectory().getAbsolutePath()
                            + jarEntry.getName()
                            .substring(TEMPLATES_PATH.length() - 1)));
                } else if (StringUtils.startsWithIgnoreCase(jarEntry.getName(), CONFIG_PATH)) {//复制配置文件
                    copyBreeConfig(jarEntry);
                }

            }
        } catch (IOException e) {
            throw new MojoExecutionException("获取配置信息失败!", e);
        }

    }

    /**
     * Copy bree config.
     *
     * @param jarEntry the jar entry
     * @throws IOException the io exception
     */
    private static void copyBreeConfig(JarEntry jarEntry) throws IOException {
        // Only copy files witch in config directory
        if (!StringUtils.startsWithIgnoreCase(jarEntry.getName(), CONFIG_PATH)) {
            return;
        }
        if (!StringUtils.equalsIgnoreCase(jarEntry.getName(), CONFIG_PATH)) {
            if (StringUtils.equalsIgnoreCase(jarEntry.getName(), CONFIG_PATH + "config.xml")) {
                if (!ConfInit.breeMojo.getConfig().exists()) {
                    copyAndOverWriteFile(jarEntry.getName(), breeMojo.getConfig());
                    System.out.println("初始化完成,下一步到" + breeMojo.getConfig() + "配置数据源");
                    System.exit(0);
                }
                // over write
            } else {
                copyAndOverWriteFile(jarEntry.getName(), new File(ConfInit.breeMojo.getConfig()
                    .getParent() + jarEntry.getName().substring(CONFIG_PATH.length() - 1)));
            }

        }
    }

    /**
     * Copy and over write file.
     *
     * @param sourceName the source name
     * @param outFile   the out file
     * @throws IOException the io exception
     */
    private static void copyAndOverWriteFile(String sourceName, File outFile) throws IOException {
        // Create output path if not exists
        if (!outFile.getParentFile().exists()) {
            if (!outFile.getParentFile().mkdirs()) {
                throw new IOException("创建目录失败" + outFile.getParentFile());
            }
        }

        // ???
        if (StringUtils.indexOf(sourceName, '.') == -1) {
            return;
        }

        try (InputStream is = ConfInit.class.getResourceAsStream("/" + sourceName);
            OutputStream os = new FileOutputStream(outFile)) {
            byte[] bytes = new byte[2048];
            int n;
            while ((n = is.read(bytes)) > 0) {
                os.write(bytes, 0, n);
            }
        }
    }

}