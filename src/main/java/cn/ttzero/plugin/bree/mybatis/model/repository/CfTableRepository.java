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

package cn.ttzero.plugin.bree.mybatis.model.repository;

import java.io.File;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.ttzero.plugin.bree.mybatis.model.config.*;
import cn.ttzero.plugin.bree.mybatis.enums.TypeMapEnum;
import cn.ttzero.plugin.bree.mybatis.exception.BreeException;
import cn.ttzero.plugin.bree.mybatis.utils.StringUtil;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import cn.ttzero.plugin.bree.mybatis.enums.MultiplicityEnum;
import cn.ttzero.plugin.bree.mybatis.enums.ParamTypeEnum;
import cn.ttzero.plugin.bree.mybatis.utils.CamelCaseUtils;
import com.google.common.collect.Lists;

import static cn.ttzero.plugin.bree.mybatis.utils.ConfigUtil.getAttr;

/**
 * Created by guanquan.wang at 2019-05-24 11:51
 */
public class CfTableRepository {
    /**
     * The constant LOG.
     */
    private static final Log LOG = new SystemStreamLog();
    /**
     * The constant PARAM_PATTERN.
     */
    private static final Pattern PARAM_PATTERN = Pattern.compile("[#$]\\{(.*?)\\}");

    /**
     * The constant STAR_BRACKET. 将 select * from 替换的正则
     */
    private static final Pattern STAR_BRACKET = Pattern.compile("\\((\\s*\\*\\s*)\\)");

    /**
     * 匹配?参数
     */
    private static final Pattern QUESTION_MARK_PATTERN = Pattern.compile("\\w+\\s*=\\s*\\?");
    /**
     * 从?参数中获取 column参数
     */
    private static final Pattern QUESTION_COLUMN_PATTERN = Pattern.compile("\\w{1,}");

    /**
     * The constant FOR_DESC_SQL_P. 为mapper.java的方法准备注释
     */
    private static final String FOR_DESC_SQL_P = "\\s*<.*>\\s*";
    /**
     * The constant FOR_DESC_SQL_PN.
     */
    private static final String FOR_DESC_SQL_PN = "\\s{2,}";

    /**
     * The constant ORDER_BY_PATTERN.
     */
    private static final String ORDER_BY_PATTERN_STR = "[o|O][r|R][d|D][e|E][r|R]\\s+[b|B][y|Y]\\s+";
    /**
     * The constant GROUP_BY_PATTERN.
     */
    private static final String GROUP_BY_PATTERN_STR = "[g|G][r|R][o|O][u|U][p|P]\\s+[b|B][y|Y]\\s+";
    private static final Pattern GROUP_BY_PATTERN = Pattern.compile(GROUP_BY_PATTERN_STR);
    /**
     * The constant HAVING
     */
    private static final String HAVING_PATTERN_STR = "[h|H][a|A][v|V][i|I][n|N][g|G]\\s+";
    /**
     * The constant SELECT_FROM_PATTERN.
     * 正则表达式,贪婪匹配,勉强匹配  .* 贪婪    .*? 勉强,之匹配最近一个
     */
    private static final Pattern SELECT_FROM_PATTERN = Pattern.compile("[s|S][e|E][l|L][e|E][c|C][t|T]\\s+[\\s\\S]*?\\s+[f|F][r|R][o|O][m|M]");
    /**
     * The constant foreach
     * 匹配foreach
     */
    private static final String XPATH_FOREACH = ".//foreach";
    /**
     * The constant if
     * 匹配if
     */
    private static final String XPATH_IF = ".//if";
    /**
     * The constant when
     * 匹配when
     */
    private static final String XPATH_WHEN = ".//when";
    /**
     * The constant UNION_PATTERN_STR.
     */
    private static final Pattern UNION_PATTERN_STR = Pattern.compile("[u|U][n|N][i|I][o|O][n|N]\\s+");

    /**
     * The sheared sql tag
     */
    private Map<String, Element> sqlCache = Maps.newHashMap();

    /**
     * Gain cf table cf table.
     *
     * @param tableFile the table file
     * @return the cf table
     * @throws DocumentException the document exception
     */
    public CfTable gainCfTable(File tableFile) throws DocumentException {
        CfTable cfTable = new CfTable();

        SAXReader saxReader = new SAXReader();
        saxReader.setEntityResolver(new IgnoreDTDEntityResolver()); // ignore dtd
        Document document = saxReader.read(tableFile);
//        LOG.info(tableFile.getPath());

        Element table = document.getRootElement();

        cfTable.setSqlname(getAttr(table, "sqlName"));
        cfTable.setPhysicalName(getAttr(table, "physicalName"));
        cfTable.setRemark(getAttr(table, "remark"));
        cfTable.setSequence(getAttr(table, "sequence"));

        // fill sql tag
        fillSql(cfTable, table);

        fillColumns(cfTable, table);

        fillResultMap(cfTable, table);

        fillOperation(cfTable, table);

        return cfTable;
    }

    /**
     * Fill result map.
     *
     * @param cfTable the cf table
     * @param table   the table
     */
    @SuppressWarnings({"unchecked", "retype"})
    private void fillResultMap(CfTable cfTable, Element table) {
        List<Element> elements = table.elements("resultMap");
        for (Element e : elements) {
            CfResultMap cfResultMap = new CfResultMap();
            cfResultMap.setName(getAttr(e, "name"));
            cfResultMap.setType(getAttr(e, "type"));
            cfResultMap.setRemark(getAttr(e, "remark"));
            List<Element> ers = e.elements();
            for (Element er : ers) {
                String name = er.getName();
                switch (name) {
                    case "collection":
                        CfCollection collection = new CfCollection();
                        collection.setProperty(getAttr(er, "property"));
                        collection.setOfType(getAttr(er, "ofType"));
                        collection.setRemark(getAttr(er, "remark"));
                        collection.setResultMap(getAttr(er, "resultMap"));
                        deepCollection(collection, er);
                        cfResultMap.addCollection(collection);
                        break;
                    case "association":
                        CfAssociation association = new CfAssociation();
                        association.setProperty(getAttr(er, "property"));
                        association.setColumn(getAttr(er, "column"));
                        association.setJavaType(getAttr(er, "javaType"));
                        association.setRemark(getAttr(er, "remark"));
                        association.setSelect(getAttr(er, "select"));
                        association.setResultMap(getAttr(er, "resultMap"));
                        deepAssociation(association, er);
                        cfResultMap.addAssociation(association);
                        break;
                    default:
                        CfColumn cfColumn = ele2Column(er);
                        cfResultMap.addColumn(cfColumn);
                }
            }
            cfTable.addResultMap(cfResultMap);
        }
    }

    /**
     * Deep parse collection tag
     *
     * @param collection the collection tag
     * @param e          Element
     */
    private void deepCollection(CfCollection collection, Element e) {
        @SuppressWarnings({"unchecked", "retype"})
        List<Element> ers = e.elements();
        for (Element er : ers) {
            String name = er.getName();
            switch (name) {
                case "collection":
                    CfCollection coll = new CfCollection();
                    coll.setProperty(getAttr(er, "property"));
                    coll.setOfType(getAttr(er, "ofType"));
                    coll.setRemark(getAttr(er, "remark"));
                    coll.setResultMap(getAttr(er, "resultMap"));
                    deepCollection(coll, er);
                    collection.addCollection(coll);
                    break;
                case "association":
                    CfAssociation association = new CfAssociation();
                    association.setProperty(getAttr(er, "property"));
                    association.setColumn(getAttr(er, "column"));
                    association.setJavaType(getAttr(er, "javaType"));
                    association.setRemark(getAttr(er, "remark"));
                    association.setSelect(getAttr(er, "select"));
                    association.setResultMap(getAttr(er, "resultMap"));
                    deepAssociation(association, er);
                    collection.addAssociation(association);
                    break;
                default:
                    CfColumn cfColumn = ele2Column(er);
                    collection.addColumn(cfColumn);
            }
        }
    }

    /**
     * Deep parse association tag
     *
     * @param association the association tag
     * @param e           Element
     */
    private void deepAssociation(CfAssociation association, Element e) {
        @SuppressWarnings({"unchecked", "retype"})
        List<Element> ers = e.elements();
        for (Element er : ers) {
            String name = er.getName();
            switch (name) {
                case "collection":
                    CfCollection coll = new CfCollection();
                    coll.setProperty(getAttr(er, "property"));
                    coll.setOfType(getAttr(er, "ofType"));
                    coll.setRemark(getAttr(er, "remark"));
                    coll.setResultMap(getAttr(er, "resultMap"));
                    deepCollection(coll, er);
                    association.addCollection(coll);
                    break;
                case "association":
                    CfAssociation asso = new CfAssociation();
                    asso.setProperty(getAttr(er, "property"));
                    asso.setColumn(getAttr(er, "column"));
                    asso.setJavaType(getAttr(er, "javaType"));
                    asso.setRemark(getAttr(er, "remark"));
                    asso.setSelect(getAttr(er, "select"));
                    asso.setResultMap(getAttr(er, "resultMap"));
                    deepAssociation(asso, er);
                    association.addAssociation(asso);
                    break;
                default:
                    CfColumn cfColumn = ele2Column(er);
                    association.addColumn(cfColumn);
            }
        }
    }

    /**
     * Element to column
     *
     * @param er Element
     * @return
     */
    private CfColumn ele2Column(Element er) {
        boolean isKey;
        if ((isKey = "id".equals(er.getName())) || "column".equals(er.getName())) {
            CfColumn cfColumn = new CfColumn();
            if (isKey) {
                cfColumn.setName(getAttr(er, "column"));
                cfColumn.setKey(getAttr(er, "property"));
            } else {
                cfColumn.setName(getAttr(er, "name"));
                cfColumn.setRemark(getAttr(er, "remark"));
                cfColumn.setRelatedColumn(getAttr(er, "relatedColumn"));
            }
            cfColumn.setJavaType(getAttr(er, "javaType"));
            cfColumn.setSqlType(getAttr(er, "jdbcType"));
            return cfColumn;
        } else {
            // TODO 标签不支持，后期可以增加
            throw new BreeException("resultMap中有不支持的标签[" + er.getName() + "]");
        }
    }

    /**
     * Fill operation.
     *
     * @param cfTable the cf table
     * @param table   the table
     */
    private void fillOperation(CfTable cfTable, Element table) {
        @SuppressWarnings({"unchecked", "retype"})
        List<Element> elements = table.elements("operation");
        for (Element e : elements) {
            CfOperation cfOperation = new CfOperation();
            cfOperation.setRemark(getAttr(e, "remark"));
            cfOperation.setName(getAttr(e, "name"));
            cfOperation.setMultiplicity(MultiplicityEnum.getByCode(getAttr(e, "multiplicity")));
            cfOperation.setVo(getAttr(e, "vo"));
            if (cfOperation.getMultiplicity() == MultiplicityEnum.paging) {
                Validate.notEmpty(cfOperation.getVo(), "需要设置paging,用来生成分页类");
            }
            cfOperation.setParamType(ParamTypeEnum.getByCode(getAttr(e, "paramType")));
            cfOperation.setResultMap(getAttr(e, "resultMap"));
            cfOperation.setResultType(getAttr(e, "resultType"));
            String timeout = getAttr(e, "timeout");
            if (StringUtil.isNotEmpty(timeout)) {
                cfOperation.setTimeout(Long.valueOf(timeout));
            }
            cfOperation.setNoCount(getAttr(e, "noCount"));

            //sql内容
            //setCfOperationCdata
            setCfOperationCdata(cfTable, e, cfOperation, table);

            fillOperationParams(e, cfOperation);

            cfTable.addOperation(cfOperation);
        }
    }

    /**
     * Search sql tags
     *
     * @param cfTable the cf table
     * @param table the xml mapper
     */
    private void fillSql(CfTable cfTable, Element table) {
        @SuppressWarnings({"unchecked", "retype"})
        List<Element> elements = table.elements("sql");
        for (Element e : elements) {
            String id = getAttr(e, "id");
            if (sqlCache.containsKey(id)) {
                throw new BreeException("Sql id重复：" + id);
            }
            sqlCache.put(id, e);
            Attribute remark = e.attribute("remark");
            if (remark != null) {
                e.remove(remark);
                cfTable.addSqlTag(id, e.asXML(), remark.getValue());
            } else {
                cfTable.addSqlTag(id, e.asXML(), null);
            }
        }
    }

    /**
     * Sets cf operation cdata.
     *
     * @param cfTable     the cf table
     * @param e           the e
     * @param cfOperation the cf operation
     */
    private void setCfOperationCdata(CfTable cfTable, Element e, CfOperation cfOperation, Element table) {
        String cdata = getContent(e);

        // SQlDESC
        String sqlDesc = cdata.replaceAll(FOR_DESC_SQL_P, " "); // \\s*<.*>\\s*
        sqlDesc = sqlDesc.replaceAll(FOR_DESC_SQL_PN, " "); // \\s{2,}
        cfOperation.setSqlDesc(sqlDesc);

        String text = e.getTextTrim();
        // 替换select *
        if (StringUtils.indexOf(text, "*") > 0) {
            // \\((\\s*\\*\\s*)\\)
            Matcher m = STAR_BRACKET.matcher(text);
            if (!m.find()) {
                if (StringUtils.isEmpty(cfOperation.getResultMap())) {
                    // TODO 替换*前要找到指定的表或子查询
                    cdata = StringUtils.replace(cdata, "*", "<include refid=\"Base_Column_List\" />");
                }
            }
        }
        // ? 参数替换 不指定类型
        cdata = delQuestionMarkParam(cdata, cfOperation, cfTable);

        cfOperation.setCdata(cdata);
        // 添加sql注释,以便于DBA 分析top sql 定位
//        cfOperation.setCdata(addSqlAnnotation(cdata, cfOperation.getName(), cfTable.getSqlname()));
        // TODO 普通vo
        // pageCount添加
        if (!cfOperation.isNoCount()) {
            setCfOperationPageCdata(table, cdata, cfOperation);
        }
    }

    /**
     * ? 参数替换
     *
     * @param cdata
     * @param cfOperation
     * @param cfTable
     * @return
     */
    private String delQuestionMarkParam(String cdata, CfOperation cfOperation, CfTable cfTable) {
        //
        if (!StringUtils.contains(cdata, '?')) {
            return cdata;
        }
//        cfTable.getColumns();
        if (StringUtils.startsWithIgnoreCase(cfOperation.getName(), "insert")) {
            //TODO cdata中 insert ? 参数替换 不指定类型
            String sql = cdata;
            //sql 特殊处理一下
            sql = sql.replaceAll("\\s{1,}", "");
            sql = sql.replaceAll("\\(\\)", "");
            sql = sql.replaceAll("\\(", "\n(\n");
            sql = sql.replaceAll("\\)", "\n)\n");

            String[] sqlLines = StringUtils.split(sql, "\n");

            int i = 0;
            for (String sqlLine : sqlLines) {
                if (StringUtils.startsWith(sqlLine, "(")) {
                    break;
                }
                i++;
            }
            String insertLine = sqlLines[i + 1];
            String valueLine = sqlLines[i + 5];
            valueLine = valueLine.replaceAll("\\w{1},\\w{1}", "");
            String[] columns = StringUtils.split(insertLine, ',');
            String[] params = StringUtils.split(valueLine, ',');

            for (int j = 0; j < params.length; j++) {
                if (StringUtils.equals(params[j], "?")) {
                    try {
                        String columnParam = CamelCaseUtils.toCamelCase(columns[j]);
                        cdata = StringUtils.replace(cdata, "?", "#{" + columnParam + "}", 1);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw new BreeException("参数设置错误#{}中,未正确使用 table=" + cfTable.getSqlname());
                    }

                }
            }

        } else {
            // \\w+\\s*=\\s*\\?
            Matcher questionMarkPatternResult = QUESTION_MARK_PATTERN.matcher(cdata);
            while (questionMarkPatternResult.find()) {
                // \\w{1,}
                Matcher columnMatcher = QUESTION_COLUMN_PATTERN.matcher(questionMarkPatternResult
                    .group());
                while (columnMatcher.find()) {
                    String columnParam = CamelCaseUtils.toCamelCase(columnMatcher.group());
                    cdata = StringUtils.replace(cdata, "?", "#{" + columnParam + "}", 1);
                    cfOperation.addPrimitiveParam(columnParam, "");
                }
            }
        }
        return cdata;
    }

    private static final String REPLACE_TMP = " ( ⊙ o ⊙ ) ";

    /**
     * Sets cf operation page cdata.
     *
     * @param cdata       the cdata
     * @param cfOperation the cf operation
     */
    void setCfOperationPageCdata(Element e, String cdata, CfOperation cfOperation) {
        // 分页配置
        if (cfOperation.getMultiplicity() != MultiplicityEnum.paging) {
            return;
        }

        // 判断是否已有count语句
        @SuppressWarnings({"unchecked", "retype"})
        List<Element> list = e.selectNodes("//operation[@name='" + cfOperation.getName() + "Count']");
        // 如果已有count语句则跳过
        if (list != null && !list.isEmpty()) {
            return;
        }

        String forCount;
        Matcher unionMatcher = UNION_PATTERN_STR.matcher(cdata);
        if (!unionMatcher.find()) {
            // 替换select字段中包含select子查询
            forCount = markQueryString(cdata);

            // 删除未尾的group by having 或 order by 语句
            Matcher selectFromMather = SELECT_FROM_PATTERN.matcher(forCount);
            if (selectFromMather.find()) {
                Matcher groupByMatcher = GROUP_BY_PATTERN.matcher(forCount);
                if (groupByMatcher.find()) {
                    // 查询group by 子句
                    String[] groupBy = markGroupBy(forCount);
                    if (groupBy != null) {
                        String group = groupBy[0];
                        forCount = groupBy[1];
                        int index = StringUtils.indexOfIgnoreCase(forCount, " from ") + 6;

                        int g;
                        boolean having = (g = StringUtils.indexOfIgnoreCase(group, " having ")) > -1;
                        // 如果having子句有聚合函数，则不能将group by去掉
                        if (having && group.indexOf('(', g) > -1) {
                            forCount = "SELECT\n        COUNT(*) AS total\n        FROM (\n        " + forCount + "\n        ) t";
                        } else {
                            // 如果having子句没有聚合函数，则将having转为where，合并执行顺序
                            if (having) {
                                int n = Integer.parseInt(groupBy[2]);
                                forCount = forCount.substring(0, n) + " and "
                                    + group.substring(g + " having ".length()) + forCount.substring(n);
                                group = group.substring(0, g);
                            }
                            forCount = "SELECT\n        COUNT(DISTINCT "
                                + group.substring(StringUtils.indexOfIgnoreCase(group, " by ") + 4)
                                + ") AS total \n        FROM\n        " + forCount.substring(index);
                        }

                    } else {
                        forCount = selectFromMather.replaceFirst("SELECT\n        COUNT(*) AS total \n        FROM\n        ");
                    }
                } else {
                    forCount = selectFromMather.replaceFirst("SELECT\n        COUNT(*) AS total \n        FROM\n        ");
                }
            }
            // 如果包含union语句，则直接在外面包count
        } else {
            forCount = "SELECT\n        COUNT(*) AS total \n        FROM (\n        " + cdata + "\n        ) t";
        }

        String cdataCount = forCount.replaceAll(ORDER_BY_PATTERN_STR, REPLACE_TMP);
//        cdataCount = cdataCount.replaceAll(GROUP_BY_PATTERN_STR, REPLACE_TMP);
        int indexOf = cdataCount.indexOf(REPLACE_TMP);
        if (indexOf > 0) {
            cdataCount = cdataCount.substring(0, indexOf)
                .replaceAll("(?m)^\\s*$" + System.lineSeparator(), "")
            ;
        }

        cfOperation.setCdataPageCount(cdataCount);

    }

    /**
     * 去掉select上方的子查询
     *
     * @param forCount
     * @return
     */
    private String markQueryString(String forCount) {
        int hIndex = forCount.indexOf('(');
        // 无子查询
        if (hIndex == -1) {
            return forCount;
        }
        final String from = " from ";
        String queryString = forCount.replaceAll("\t|\n", " ").replace("\r\n", "  ");
        int fromIndex = StringUtils.lastIndexOfIgnoreCase(queryString, from, hIndex);
        if (fromIndex > -1) {
            return forCount;
        }

        int start = hIndex, c = 1, delete_count = 0;
        StringBuilder buf = new StringBuilder(queryString);
        do {
            int eIndex = queryString.indexOf(')', hIndex) + 1;
            while (c > 0 && eIndex > 0) {
                // 中间部分可能是聚合函数，或者是一个完整的子句
                for (int i = hIndex + 1; i < eIndex; i++) {
                    if (queryString.charAt(i) == '(') {
                        c++;
                    } else if (queryString.charAt(i) == ')') {
                        c--;
                    }
                }
                if (c != 0) {
                    hIndex = eIndex;
                    eIndex = queryString.indexOf(')', eIndex) + 1;
                }
            }
            buf.delete(start, eIndex);
            queryString = buf.toString();
            delete_count += eIndex - start;

            start = hIndex = queryString.indexOf('(', start);
            c++;
            if (hIndex == -1) {
                break;
            }
            fromIndex = StringUtils.lastIndexOfIgnoreCase(queryString, from, hIndex);
            if (fromIndex > -1) {
                break;
            }
        } while (true);

        // from 后面的语句保留格式
        int index = StringUtils.indexOfIgnoreCase(queryString, from);
        return buf.delete(index, buf.length()).append(forCount.substring(index + delete_count)).toString();
    }

    /**
     * 查询末尾group by
     *
     * @param forCount
     * @return
     */
    private String[] markGroupBy(String forCount) {
        String queryString = forCount.replaceAll("\t|\n", " ").replace("\r\n", "  ");
        int andIndex = StringUtils.lastIndexOfIgnoreCase(queryString, " and "), eIndex = queryString.lastIndexOf(')');
        // 无子查询
        if (andIndex == -1) {
            andIndex = StringUtils.lastIndexOfIgnoreCase(queryString, " where ");
        }
        // 最后出来的and 或 where
        int index = andIndex < eIndex ? eIndex : andIndex;

        Matcher groupByMatcher = GROUP_BY_PATTERN.matcher(queryString.substring(index));
        if (groupByMatcher.find()) {
            int orderBy = StringUtils.indexOfIgnoreCase(queryString, " order ", index);
            if (orderBy == -1) {
                orderBy = StringUtils.indexOfIgnoreCase(queryString, " limit ", index);
            }
            if (orderBy == -1) {
                orderBy = queryString.length();
            }
            index = StringUtils.indexOfIgnoreCase(queryString, " group ", index);
            String groupBy = queryString.substring(index, orderBy);
            String t = forCount.substring(0, index);
            if (orderBy != queryString.length()) {
                t += forCount.substring(orderBy);
            }
            String[] result = new String[3];
            result[0] = groupBy;
            result[1] = t;
            result[2] = String.valueOf(orderBy - groupBy.length());

            return result;
        }
        return null;
    }

//    /**
//     * Add sql annotation string.
//     *
//     * @param cdata  the cdata
//     * @param oName  the o name
//     * @param tbName the tb name
//     * @return the string
//     */
//    private String addSqlAnnotation(String cdata, String oName, String tbName) {
//
//        String sqlAnnotation = StringUtils.upperCase(CamelCaseUtils.toInlineName(CamelCaseUtils
//            .toCamelCase("ms_" + tbName + "_" + oName)));
//        if (StringUtils.startsWithIgnoreCase(oName, "insert ")
//            || StringUtils.startsWithIgnoreCase(oName, "update")
//            || StringUtils.startsWithIgnoreCase(oName, "delete")) {
//            if (StringUtils.contains(cdata, "update ")) {
//                return StringUtils.replace(cdata, "update ", "update /*" + sqlAnnotation + "*/ ");
//            }
//            if (StringUtils.contains(cdata, "UPDATE ")) {
//                return StringUtils.replace(cdata, "UPDATE ", "UPDATE /*" + sqlAnnotation + "*/ ");
//            }
//            if (StringUtils.contains(cdata, "insert ")) {
//                return StringUtils.replace(cdata, "insert ", "insert /*" + sqlAnnotation + "*/ ");
//            }
//            if (StringUtils.contains(cdata, "INSERT ")) {
//                return StringUtils.replace(cdata, "INSERT ", "INSERT /*" + sqlAnnotation + "*/ ");
//            }
//            if (StringUtils.contains(cdata, "delete ")) {
//                return StringUtils.replace(cdata, "delete ", "delete /*" + sqlAnnotation + "*/ ");
//            }
//            if (StringUtils.contains(cdata, "DELETE ")) {
//                return StringUtils.replace(cdata, "DELETE ", "DELETE /*" + sqlAnnotation + "*/ ");
//            }
//        } else {
//            if (StringUtils.contains(cdata, "select ")) {
//                return StringUtils.replace(cdata, "select ", "select /*" + sqlAnnotation + "*/ ");
//            }
//            if (StringUtils.contains(cdata, "SELECT ")) {
//                return StringUtils.replace(cdata, "SELECT", "SELECT /*" + sqlAnnotation + "*/ ");
//            }
//        }
//
//        return cdata;
//    }

    /**
     * Fill operation params. 原生态参数获取 添加List参数支持
     *
     * @param e           the e
     * @param cfOperation the cf operation
     */
    private void fillOperationParams(Element e, CfOperation cfOperation) {

        if (cfOperation.getParamType() != ParamTypeEnum.primitive) {
            return;
        }

        // TODO 对象里包含对象逻辑
//        LOG.info("---------------" + cfOperation.getName() + "-----------------");

        String content = getReplaceInclude(e);
        Element newElement = stringToXml(content);
        // #\\{(.*?)\\}
        Matcher m = PARAM_PATTERN.matcher(content);
        List<String> params = Lists.newArrayList();
        while (m.find()) {
            params.add(m.group(1));
        }
        for (String p : params) {
            String getAttr = null;
            String type = null;
            for (String s : StringUtils.split(p, ",")) {
                if (s.indexOf('=') > -1) {
                    s = StringUtils.trim(s);
                    if (StringUtils.startsWithIgnoreCase(s, "javaType")
                        || StringUtils.startsWithIgnoreCase(s, "jdbcType")) {
                        type = StringUtils.split(s, "=")[1].trim().replace("\"", "");
                    }
                } else {
                    getAttr = s;
                }
            }
            cfOperation.addPrimitiveParam(getAttr, type);
        }

        // if & when 语句上 test 参数添加
        @SuppressWarnings({"unchecked", "retype"})
        List<Element> ifs = newElement.selectNodes(XPATH_IF), whens = newElement.selectNodes(XPATH_WHEN);
        ifs.addAll(whens);
        for (Element ele : ifs) {
            Attribute getAttr = ele.attribute("test");
            Validate.notNull(getAttr, "<if> 或 <when> 元素未配置test属性 table=" + cfOperation.getName());
            String test = getAttr.getValue();
            Validate.notEmpty(test, "<if> 或 <when> 元素配置test属性值空 table=" + cfOperation.getName());
            String[] tests = test.split(" and | or | AND | OR ");
            for (String t : tests) {
                String[] kv = getKV(t.trim());
//                LOG.info("if / when " + Arrays.toString(kv) +" " + t);
                // Key 不存在时处理
                if (cfOperation.containsParam(kv[0]) && !TypeMapEnum.OTHER.getJdbcType().equalsIgnoreCase(cfOperation.getPrimitiveParams().get(kv[0])) || kv.length <= 1) {
                    continue;
                }
//                if (kv.length == 1) continue;

                char c = kv[1].charAt(0);
                if (c == '\'' || c == '"') {
                    cfOperation.addPrimitiveParam(kv[0], TypeMapEnum.VARCHAR.getJdbcType());
                } else if (c >= '0' && c <= '9') {
                    if (kv[1].indexOf('.') > -1) {
                        cfOperation.addPrimitiveParam(kv[0], TypeMapEnum.DECIMAL.getJdbcType());
                    } else {
                        cfOperation.addPrimitiveParam(kv[0], TypeMapEnum.INTEGER.getJdbcType());
                    }
                } else {
                    cfOperation.addPrimitiveParam(kv[0], TypeMapEnum.OTHER.getJdbcType());
                }
            }
        }

        // 使用XPath匹配foreach
        @SuppressWarnings({"unchecked", "retype"})
        List<Element> items = newElement.selectNodes(XPATH_FOREACH);
        for (Element item : items) {
            String collName = getAttr(item, "collection");
            String itemName = getAttr(item, "item");
            Validate.notEmpty(collName, "foreach 元素设置错误 table=" + cfOperation.getName());
            Validate.notEmpty(itemName, "foreach 元素设置错误 table=" + cfOperation.getName());
            cfOperation.addPrimitiveForeachParam(itemName, collName);
        }
    }

    /**
     * Get key from test getAttribute
     *
     * @param getAttr
     * @return
     */
    private String[] getKV(String getAttr) {
        String[] kvs = getAttr.split("!=|==|=");
        if (kvs.length == 1) {
            kvs[0] = StringUtils.trim(kvs[0]);
        } else {
            String k = kvs[0].replace('(', ' ').trim();
            char c = k.charAt(0);
            if (c == '\'' || c == '"' || (c >= '0' && c <= '9')) {
                String v = kvs[1];
                kvs[1] = k;
                kvs[0] = v.trim();
            } else {
                kvs[0] = StringUtils.trim(kvs[0]);
                kvs[1] = StringUtils.trim(kvs[1]);
            }
        }
        return kvs;
    }

    /**
     * Fill columns.
     *
     * @param cfTable the cf table
     * @param table   the table
     */
    private void fillColumns(CfTable cfTable, Element table) {
// TODO root 下没有直接的column节点
        @SuppressWarnings({"unchecked", "retype"})
        List<Element> elements = table.elements("column");
        for (Element e : elements) {
            CfColumn cfColumn = new CfColumn();
            cfColumn.setName(getAttr(e, "name"));
            cfColumn.setJavaType(getAttr(e, "javaType"));
            cfColumn.setRelatedColumn(getAttr(e, "relatedColumn"));
            cfTable.addColumn(cfColumn);
        }

    }

    /**
     * Returns the element's content
     *
     * @param e current element
     * @return element's innerHTML
     */
    private String getContent(Element e) {
        String cXml = e.asXML();
        String[] lines = StringUtils.split(cXml, "\n");
        StringBuilder buffer = new StringBuilder(lines[1]);
        for (int i = 2; i < lines.length - 1; i++) {
            buffer.append("\n").append(lines[i]);
        }
        return buffer.toString();
    }

    /**
     * Returns the element's content witch replace include tag
     * with include content
     *
     * @param e current element
     * @return element's innerHTML
     */
    private String getReplaceInclude(Element e) {
        String cdata = e.asXML();
        // If has include tag
        @SuppressWarnings({"unchecked", "retype"})
        List<Element> includeArray = e.elements("include");
        if (!includeArray.isEmpty()) {
            for (Element ic : includeArray) {
                String refid = getAttr(ic, "refid");
                if ("Base_Column_List".equalsIgnoreCase(refid)) {
                    continue;
                }
                if (StringUtil.isEmpty(refid)) {
                    throw new BreeException("operation["+getAttr(e, "name")
                        +"]包含include节点但是未指定refid值。");
                }
                Element ref = sqlCache.get(refid);
                if (ref == null) {
                    throw new BreeException("operation["+getAttr(e, "name")
                        +"]包含include节点但是refidu并未出现在此xml中。");
                }
                cdata = cdata.replace(ic.asXML(), ref.asXML());
            }
        }
        return cdata;
    }

    /**
     *  Convert string to xml element
     * @param string the xml string
     * @return the {@link Element}
     */
    private Element stringToXml(String string) {
        try {
            SAXReader reader = new SAXReader();
            Document doc = reader.read(new StringReader(string));
            return doc.getRootElement();
        } catch (DocumentException e) {
            throw new BreeException("", e);
        }
    }

}