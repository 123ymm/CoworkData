package com.huawei.coworkdata.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.huawei.coworkdata.mapper")
public class MybatisPlusConfig {
}
