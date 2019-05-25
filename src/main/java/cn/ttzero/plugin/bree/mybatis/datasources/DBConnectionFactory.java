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

package cn.ttzero.plugin.bree.mybatis.datasources;

import java.sql.Connection;

import cn.ttzero.plugin.bree.mybatis.utils.ConfigUtil;
import org.apache.commons.lang.Validate;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

/**
 * Created by guanquan.wang at 2019-05-24 21:50
 */
public class DBConnectionFactory {
    /**
     * The constant LOG.
     */
    private static final Log LOG = new SystemStreamLog();

    /**
     * Gets connection.
     *
     * @return the connection
     */
    public static Connection getConnection() {
        Connection connection = null;
        try {
            LOG.info("==== init connection");
            connection = JdbcConnection.getConnection(ConfigUtil.getCurrentDb());
            Validate.notNull(connection, "====connection error mysql");

        } catch (Exception e) {
            LOG.error("", e);
        }
        return connection;

    }

}
