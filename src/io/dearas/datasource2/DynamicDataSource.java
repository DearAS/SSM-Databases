package io.dearas.datasource2;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 动态数据源实现读写分离
 */
public class DynamicDataSource extends AbstractRoutingDataSource {

    private Object writeDataSource; //写数据源

    private List<Object> readDataSources; //多个读数据源

    private int readDataSourceSize; //读数据源个数

    private int readDataSourcePollPattern = 0; //获取读数据源方式，0：随机，1：轮询

    private AtomicLong counter = new AtomicLong(0);

    private static final Long MAX_POOL = Long.MAX_VALUE;

    private final Lock lock = new ReentrantLock();

    @Override
    public void afterPropertiesSet() {
        if (this.writeDataSource == null) {
            throw new IllegalArgumentException("Property 'writeDataSource' is required");
        }
        setDefaultTargetDataSource(writeDataSource);
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DynamicDataSourceGlobal.WRITE.name(), writeDataSource);
        if (this.readDataSources == null) {
            readDataSourceSize = 0;
        } else {
            for(int i=0; i<readDataSources.size(); i++) {
                targetDataSources.put(DynamicDataSourceGlobal.READ.name() + i, readDataSources.get(i));
            }
            readDataSourceSize = readDataSources.size();
        }
        setTargetDataSources(targetDataSources);
        super.afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {

        DynamicDataSourceGlobal dynamicDataSourceGlobal = DynamicDataSourceHolder.getDataSource();

        if(dynamicDataSourceGlobal == null
                || dynamicDataSourceGlobal == DynamicDataSourceGlobal.WRITE
                || readDataSourceSize <= 0) {
            return DynamicDataSourceGlobal.WRITE.name();
        }

        int index = 1;
        // 如果readDataSourcePollPattern = 1则说明是轮询方式
        if(readDataSourcePollPattern == 1) {
            /**
             * 为了保证线程安全，count是AtomicLong类型，是原子变量。
             * incrementAndGet() 相当于 i++ 先引用后+1
             * getAndIncrement() 相当于 ++i 先+1后引用
             */
            long currValue = counter.incrementAndGet();
            if((currValue + 1) >= MAX_POOL) {
                try {
                    /**
                     *  Lock lock = new ReentrantLock();
                     *  使用java lock机制
                     */
                    lock.lock();
                    if((currValue + 1) >= MAX_POOL) {   //再次判断
                        counter.set(0);
                    }
                } finally {
                    lock.unlock();
                }
            }
            index = (int) (currValue % readDataSourceSize);
        } else {
            // 随机方式
            /**
             * ThreadLocalRandom是JDK 7之后提供并发产生随机数，能够解决多个线程发生的竞争争夺。ThreadLocalRandom不是直接用new实例化，
             * 而是第一次使用其静态方法current()。从Math.random()改变到ThreadLocalRandom有如下好处：
             * 我们不再有从多个线程访问同一个随机数生成器实例的争夺。取代以前每个随机变量实例化一个随机数生成器实例，我们可以每个线程实例化一个。
             */
            index = ThreadLocalRandom.current().nextInt(0, readDataSourceSize);
        }
        return dynamicDataSourceGlobal.name() + index;
    }

    public void setWriteDataSource(Object writeDataSource) {
        this.writeDataSource = writeDataSource;
    }

    public void setReadDataSources(List<Object> readDataSources) {
        this.readDataSources = readDataSources;
    }

    public void setReadDataSourcePollPattern(int readDataSourcePollPattern) {
        this.readDataSourcePollPattern = readDataSourcePollPattern;
    }
}