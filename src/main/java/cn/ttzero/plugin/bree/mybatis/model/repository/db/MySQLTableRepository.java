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

package cn.ttzero.plugin.bree.mybatis.model.repository.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import cn.ttzero.plugin.bree.mybatis.model.config.CfTable;
import cn.ttzero.plugin.bree.mybatis.model.db.Database;
import cn.ttzero.plugin.bree.mybatis.model.dbtable.Table;
import cn.ttzero.plugin.bree.mybatis.utils.ConfigUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.type.JdbcType;

import cn.ttzero.plugin.bree.mybatis.enums.TypeMapEnum;
import cn.ttzero.plugin.bree.mybatis.model.dbtable.Column;
import cn.ttzero.plugin.bree.mybatis.model.dbtable.PrimaryKeys;
import cn.ttzero.plugin.bree.mybatis.utils.CamelCaseUtils;

/**
 * Created by guanquan.wang at 2019-05-24 09:02
 */
public class MySQLTableRepository {

    /**
     * Gain table table.
     *
     * @param connection the connection
     * @param tableName the table name
     * @param cfTable the cf table
     * @return the table
     * @throws SQLException the sql exception
     */
    public Table gainTable(Connection connection, String tableName, CfTable cfTable)
            throws SQLException {
        String physicalName = cfTable == null ? tableName : cfTable.getPhysicalName();
        String logicName = tableName;
        Database database = ConfigUtil.getCurrentDb();
        for (String splitTableSuffix : database.getSplitSuffixs()) {
            if (StringUtils.endsWithIgnoreCase(tableName, splitTableSuffix)) {
                logicName = StringUtils.replace(logicName, splitTableSuffix, "");
                break;
            }
        }

        List<Column> cfColumns = cfTable == null ? null : cfTable.getColumns();
        DatabaseMetaData databaseMetaData = connection.getMetaData();


        // 生成table
        Table table = new Table();
        table.setName(logicName);
        for (String pre : database.getPrefixs()) {
            if (!StringUtils.endsWith(pre, "_")) {
                pre = pre + "_";
            }

            if (StringUtils.startsWith(logicName, StringUtils.upperCase(pre))) {
                table.setJavaName(CamelCaseUtils.toCapitalizeCamelCase(StringUtils.substring(
                        logicName, pre.length())));
                break;/* 取第一个匹配的 */
            }
        }
        if (StringUtils.isBlank(table.getJavaName())) {
            table.setJavaName(CamelCaseUtils.toCapitalizeCamelCase(logicName));
        }
        table.setPhysicalName(physicalName);
        table.setRemark(logicName);


        // 填充字段
        fillColumns(connection, physicalName, databaseMetaData, table, cfColumns);

        // 主键
        fillPrimaryKeys(connection, physicalName, databaseMetaData, table);

        // 自动生成初始操作
        table.setCreateDefaultOperation(ConfigUtil.config.isCreateDefaultOperation());

        return table;
    }

    /**
     * Fill primary keys.
     *
     * @param connection the connection
     * @param tableName the table name
     * @param databaseMetaData the database meta data
     * @param table the table
     * @throws SQLException the sql exception
     */
    private void fillPrimaryKeys(Connection connection, String tableName,
                                 DatabaseMetaData databaseMetaData, Table table)
            throws SQLException {
        PrimaryKeys primaryKeys = null;

        ResultSet resultSet = databaseMetaData.getPrimaryKeys(connection.getCatalog(),
                connection.getSchema(), tableName);

        while (resultSet.next()) {
            for (Column column : table.getColumnList()) {
                if (StringUtils.equals(column.getColumn(), Str(resultSet, "COLUMN_NAME"))) {
                    primaryKeys = primaryKeys == null ? new PrimaryKeys() : primaryKeys;
                    primaryKeys.addColumn(column);
                    column.setPrimaryKey(true);
                    String pkName = resultSet.getString("PK_NAME");
                    pkName = StringUtils.isBlank(pkName) ? column.getColumn() : pkName;
                    primaryKeys.setPkName(CamelCaseUtils.toCapitalizeCamelCase(pkName));
                }
            }
        }
        table.setPrimaryKeys(primaryKeys);
    }

    /**
     * Fill columns.
     *
     * @param connection the connection
     * @param tableName the table name
     * @param databaseMetaData the database meta data
     * @param table the table
     * @param cfColumns the cf columns
     * @throws SQLException the sql exception
     */
    private void fillColumns(Connection connection, String tableName,
                             DatabaseMetaData databaseMetaData, Table table,
                             List<Column> cfColumns) throws SQLException {
        // 指定表字段
        ResultSet resultSet = databaseMetaData.getColumns(connection.getCatalog(), null, tableName, null);

        // 组装字段
        while (resultSet.next()) {
            Column column = new Column();
            column.setColumn(Str(resultSet, "COLUMN_NAME"));
            column.setJdbcType(JdbcType.forCode(resultSet.getInt("DATA_TYPE")).name());
            column.setDefaultValue(Str(resultSet, "COLUMN_DEF"));
            column.setProperty(CamelCaseUtils.toCamelCase(column.getColumn()));
            column.setJavaType(getJavaType(column, cfColumns));
            column.setRemark(Str(resultSet, "REMARKS", column.getColumn()));
            table.addColumn(column);
        }
    }

    /**
     * Gets java type.
     *
     * @param column the column
     * @param cfColumns the cf columns
     * @return the java type
     */
    private String getJavaType(Column column, List<Column> cfColumns) {
        if (cfColumns != null && cfColumns.size() > 0) {
            for (Column cfColumn : cfColumns) {
                if (StringUtils.endsWithIgnoreCase(column.getColumn(), cfColumn.getColumn())) {
                    return cfColumn.getJavaType();
                }
            }
        }
        String javaType = TypeMapEnum.getByJdbcType(column.getJdbcType()).getJavaType();
        String custJavaType = ConfigUtil.config.getTypeMap().get(javaType);
        return StringUtils.isBlank(custJavaType) ? javaType : custJavaType;
    }

    /**
     * Str string.
     *
     * @param resultSet the result set
     * @param column the column def
     * @return the string
     * @throws SQLException the sql exception
     */
    private String Str(ResultSet resultSet, String column) throws SQLException {
        return StringUtils.upperCase(resultSet.getString(column));
    }

    /**
     * Str string.
     *
     * @param resultSet the result set
     * @param column the column
     * @param defaultVal the default val
     * @return the string
     * @throws SQLException the sql exception
     */
    private String Str(ResultSet resultSet, String column, String defaultVal) throws SQLException {
        String val = Str(resultSet, column);
        return StringUtils.isBlank(val) ? defaultVal : val;
    }
}
