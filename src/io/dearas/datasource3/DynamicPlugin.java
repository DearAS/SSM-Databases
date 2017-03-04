package io.dearas.datasource3;

import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;

import java.sql.Connection;
import java.util.Properties;

/**
 * 拦截器
 */
@Intercepts({ @Signature(type = StatementHandler.class, method = "prepare", args = { Connection.class }) })
public class DynamicPlugin implements Interceptor {

    public Object intercept(Invocation invocation) throws Throwable {


        Connection conn = (Connection)invocation.getArgs()[0];
        //如果是采用了我们代理,则路由数据源
        if(conn instanceof ConnectionProxy){
            StatementHandler statementHandler = (StatementHandler) invocation
                    .getTarget();

            MappedStatement mappedStatement = null;
            if (statementHandler instanceof RoutingStatementHandler) {
                StatementHandler delegate = (StatementHandler) ReflectionUtils
                        .getFieldValue(statementHandler, "delegate");
                mappedStatement = (MappedStatement) ReflectionUtils.getFieldValue(
                        delegate, "mappedStatement");
            } else {
                mappedStatement = (MappedStatement) ReflectionUtils.getFieldValue(
                        statementHandler, "mappedStatement");
            }
            String key = AbstractDynamicDataSourceProxy.WRITE;

            if(mappedStatement.getSqlCommandType() == SqlCommandType.SELECT){
                key = AbstractDynamicDataSourceProxy.READ;
            }else{
                key = AbstractDynamicDataSourceProxy.WRITE;
            }

            ConnectionProxy connectionProxy = (ConnectionProxy)conn;
            connectionProxy.getTargetConnection(key);

        }

        return invocation.proceed();

    }

    public Object plugin(Object target) {

        return Plugin.wrap(target, this);
    }

    public void setProperties(Properties properties) {
        //NOOP

    }

}