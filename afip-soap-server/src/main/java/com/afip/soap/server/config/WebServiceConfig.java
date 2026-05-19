package com.afip.soap.server.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

@EnableWs                   // Enables Spring-WS support in this application
@Configuration
public class WebServiceConfig extends WsConfigurerAdapter {

    /**
     * Registers the SOAP servlet and maps it to /ws/*
     *
     * This is the equivalent of registering a servlet in web.xml.
     * Every SOAP request must arrive at /ws/* to be processed.
     *
     * Example URL: http://localhost:8081/ws/invoice
     */
    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
            ApplicationContext applicationContext) {

        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);

        // Automatically transforms the XSD into a WSDL when requested
        servlet.setTransformWsdlLocations(true);

        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    /**
     * Generates the WSDL automatically from the XSD file.
     *
     * Spring reads afip-invoice.xsd and builds a complete WSDL from it.
     * The WSDL will be available at: http://localhost:8081/ws/invoice.wsdl
     *
     * The bean name "invoice" is what defines the URL path (/ws/invoice.wsdl)
     */
    @Bean(name = "invoice")
    public DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema invoiceSchema) {

        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();

        // The namespace must match the targetNamespace in afip-invoice.xsd
        wsdl.setTargetNamespace("http://www.afip.soap.server/invoice");

        // URL prefix where the SOAP endpoint is mounted
        wsdl.setLocationUri("/ws");

        // The XSD schema that defines request and response
        wsdl.setSchema(invoiceSchema);

        wsdl.setPortTypeName("InvoicePort");

        return wsdl;
    }

    /**
     * Loads the XSD file from src/main/resources/xsd/afip-invoice.xsd
     *
     * Spring uses this to:
     *   1. Generate the WSDL (used above)
     *   2. Validate incoming SOAP messages against the contract
     */
    @Bean
    public XsdSchema invoiceSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/afip-invoice.xsd"));
    }
}