package com.tefire.data.align.constants;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-08-01 22:07:02
 * @Description: 表常量类
 */
public class TableConstants {

    /**
     * 表名中的分隔符
     */
    private static final String TABLE_NAME_SEPARATE = "_";

    /**
     * 拼接表名后缀
     * @param hashKey
     * @return
     */
    public static String buildTableNameSuffix(String date, long hashKey) {
        // 拼接完整的表名
        return date + TABLE_NAME_SEPARATE + hashKey;
    }

}
