package io.dearas.datasource;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.NestedRuntimeException;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.ReflectionUtils;

/**
 * 这个类有两个功能：
 * 1.实现BeanPostProcessor接口，实例化每个bean对象之后，把有事务控制的bean对象摘取出来，并对它的方法进行判断，最终添加到一个map中。map的key为方法名，value为是否使用读库
 * 2.determineReadOrWriteDB方法作为AOP的切面，对使用读库还是写库进行标记。
 * 扩展点：
 * 1.通过<property name="forceChoiceReadWhenWrite" value="false"/> forceChoiceReadWhenWrite来配置在前一个操作时写操作时，下一个操作使用读库还是写库。(为了防止主从复制不及时现象)
 * 2.通过 <tx:method name="query*" read-only="true"/> 事务的read-only属性来判断是使用读库还是写库。
 * 这些判断操作都是在postProcessAfterInitialization()方法中完成的。
 */
public class ReadWriteDataSourceProcessor implements BeanPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(ReadWriteDataSourceProcessor.class);
    
    private boolean forceChoiceReadWhenWrite = false;
    
    private Map<String, Boolean> readMethodMap = new HashMap<String, Boolean>();

    /**
     * 当之前操作是写的时候，是否强制从从库读
     * 默认（false） 当之前操作是写，默认强制从写库读
     * @param forceReadOnWrite
     */
    
    public void setForceChoiceReadWhenWrite(boolean forceChoiceReadWhenWrite) {
        
        this.forceChoiceReadWhenWrite = forceChoiceReadWhenWrite;
    }
    

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    	System.out.println("AfterInitialization,实例化之后的动作。"+beanName);
    	//1.如果这个bean不是NameMatchTransactionAttributeSource类型，则跳过。表示是有事务的bean对象
        if(!(bean instanceof NameMatchTransactionAttributeSource)) {
            return bean;
        }
        
        try {
            //2. 获取事务的相关属性进行判断是否添加到readMethodMap中，添加到根据map的value是false和true判断是不是读库。
            NameMatchTransactionAttributeSource transactionAttributeSource = (NameMatchTransactionAttributeSource)bean;
            Field nameMapField = ReflectionUtils.findField(NameMatchTransactionAttributeSource.class, "nameMap");
            nameMapField.setAccessible(true);
            Map<String, TransactionAttribute> nameMap = (Map<String, TransactionAttribute>) nameMapField.get(transactionAttributeSource);
            
            for(Entry<String, TransactionAttribute> entry : nameMap.entrySet()) {
                RuleBasedTransactionAttribute attr = (RuleBasedTransactionAttribute)entry.getValue();

                //仅对read-only的处理
                if(!attr.isReadOnly()) {
                    continue;
                }
                
                String methodName = entry.getKey();
                Boolean isForceChoiceRead = Boolean.FALSE;
                if(forceChoiceReadWhenWrite) {
                    //不管之前操作是写，默认强制从读库读 （设置为NOT_SUPPORTED即可）
                    //NOT_SUPPORTED会挂起之前的事务
                    attr.setPropagationBehavior(Propagation.NOT_SUPPORTED.value());
                    isForceChoiceRead = Boolean.TRUE;
                } else {
                    //否则 设置为SUPPORTS（这样可以参与到写事务）
                    attr.setPropagationBehavior(Propagation.SUPPORTS.value());
                }
                log.debug("read/write transaction process  method:{} force read:{}", methodName, isForceChoiceRead);
                readMethodMap.put(methodName, isForceChoiceRead);
            }
            
        } catch (Exception e) {
            throw new ReadWriteDataSourceTransactionException("process read/write transaction error", e);
        }
        
        return bean;
    }
    
    
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
    	System.out.println("BeforeInitialization,实例化之前的动作。"+beanName);
        return bean;
    }

    private class ReadWriteDataSourceTransactionException extends NestedRuntimeException {
        public ReadWriteDataSourceTransactionException(String message, Throwable cause) {
            super(message, cause);
        }
    }


    /**
     *  数据源切面
     * @param pjp
     * @return
     * @throws Throwable
     */
    public Object determineReadOrWriteDB(ProceedingJoinPoint pjp) throws Throwable {
        //pjp.getSignature().getName() 返回的是切入点的方法名
        if (isChoiceReadDB(pjp.getSignature().getName())) {
            ReadWriteDataSourceDecision.markRead();     //标记此方法为读库
        } else {
            ReadWriteDataSourceDecision.markWrite();    //标记此方法为写库
        }

        try {
            return pjp.proceed();
        } finally {
            ReadWriteDataSourceDecision.reset();
        }   
    }

    /***
     * 判断是不是读库
     * @param methodName
     * @return
     */
    private boolean isChoiceReadDB(String methodName) {

        String bestNameMatch = null;
        for (String mappedName : this.readMethodMap.keySet()) {
            if (isMatch(methodName, mappedName)) {
                bestNameMatch = mappedName;
                break;
            }
        }

        Boolean isForceChoiceRead = readMethodMap.get(bestNameMatch);
        //表示强制选择 读 库
        if(isForceChoiceRead == Boolean.TRUE) {
            return true;
        }
        
        //如果之前选择了写库 现在还选择 写库
        if(ReadWriteDataSourceDecision.isChoiceWrite()) {
            return false;
        }
        
        //表示应该选择读库
        if(isForceChoiceRead != null) {
            return true;
        }
        //默认选择 写库
        return false;
    }


    protected boolean isMatch(String methodName, String mappedName) {
        return PatternMatchUtils.simpleMatch(mappedName, methodName);
    }

}