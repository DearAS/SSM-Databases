package io.dearas.datasource;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Created by Tnp.
 */
public class Test {
    public static void main(String[] args) {
        ApplicationContext context = new ClassPathXmlApplicationContext("classpath:spring-*.xml");
        JdbcTemplate template = (JdbcTemplate)context.getBean("jdbcTemplate");
        template.execute("SELECT * FROM t_ip");
    }
}