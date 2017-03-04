package io.dearas.datasource2;

import org.apache.log4j.Logger;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * 定义选择数据源切面
 */
public class DynamicDataSourceAspect {

    private static final Logger logger = Logger.getLogger(DynamicDataSourceAspect.class);

    public void pointCut(){};

    public void before(JoinPoint point)
    {
        Object target = point.getTarget();
        String methodName = point.getSignature().getName();
        Class<?>[] clazz = target.getClass().getInterfaces();
        Class<?>[] parameterTypes = ((MethodSignature) point.getSignature()).getMethod().getParameterTypes();
        try {
            Method method = clazz[0].getMethod(methodName, parameterTypes);
            if (method != null && method.isAnnotationPresent(DataSource.class)) {
                DataSource data = method.getAnnotation(DataSource.class);
                DynamicDataSourceHolder.putDataSource(data.value());
            }
        } catch (Exception e) {
            logger.error(String.format("Choose DataSource error, method:%s, msg:%s", methodName, e.getMessage()));
        }
    }

    public void after(JoinPoint point) {
        DynamicDataSourceHolder.clearDataSource();
    }
}