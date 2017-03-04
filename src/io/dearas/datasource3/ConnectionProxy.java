package io.dearas.datasource3;


import java.sql.Connection;

/**
 * Created by IDEA
 * User: mashaohua
 * Date: 2016-07-19 15:40
 * Desc: 创建Connection代理接口
 */
public interface ConnectionProxy extends Connection {

    /**
     * 根据传入的读写分离需要的key路由到正确的connection
     * @param key 数据源标识
     * @return
     */
    Connection  getTargetConnection(String key);
}
