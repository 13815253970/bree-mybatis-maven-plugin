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

package cn.ttzero.plugin.bree.mybatis.enums;

import org.apache.commons.lang.StringUtils;

/**
 * Created by guanquan.wang at 2019-05-24 21:50
 */
public enum ParamTypeEnum {
    /**
     * DO作为参数.
     */
    object("object"),
    /**
     * 多DO作为对象
     */
    multiple("multiple"),
    /**
     * 原生态参数.
     */
    primitive("primitive");

    /**
     * The Code.
     */
    private String code;

    /**
     * Instantiates a new Param type enum.
     *
     * @param code the code
     */
    ParamTypeEnum(String code) {
        this.code = code;
    }

    /**
     * Get by code param type enum.
     *
     * @param code the code
     * @return the param type enum
     */
    public static ParamTypeEnum getByCode(String code) {
        for (ParamTypeEnum paramTypeEnum : ParamTypeEnum.values()) {
            if (StringUtils.equals(code, paramTypeEnum.code)) {
                return paramTypeEnum;
            }
        }
        return ParamTypeEnum.primitive;
    }

}