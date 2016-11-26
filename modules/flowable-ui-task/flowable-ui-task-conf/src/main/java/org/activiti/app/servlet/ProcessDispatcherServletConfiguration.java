package org.activiti.app.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@ComponentScan({ "org.activiti.rest.exception", "org.activiti.rest.service.api" })
@EnableAsync
public class ProcessDispatcherServletConfiguration extends BaseDispatcherServletConfiguration {

  protected final Logger log = LoggerFactory.getLogger(ProcessDispatcherServletConfiguration.class);

}
