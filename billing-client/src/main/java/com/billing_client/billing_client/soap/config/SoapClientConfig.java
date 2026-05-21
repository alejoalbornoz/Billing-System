package com.billing_client.billing_client.soap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

@Configuration
public class SoapClientConfig {

    @Value("${afip.soap.url}")
    private String afipSoapUrl;

    /**
     * Jaxb2Marshaller handles the conversion between Java objects and XML.
     *
     * marshalling   → Java object → XML (when sending the request)
     * unmarshalling → XML → Java object (when receiving the response)
     *
     * We tell it which package contains the JAXB-generated classes
     * so it knows how to serialize/deserialize them.
     *
     * Note: we need to copy the generated classes from afip-soap-server
     * into this project (or share them via a common module).
     * For now we reference the same package structure.
     */
    @Bean
    public Jaxb2Marshaller jaxb2Marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();

        // The package where JAXB generated classes live
        marshaller.setContextPath("server.soap.afip.invoice");

        return marshaller;
    }

    /**
     * WebServiceTemplate is the main class to send/receive SOAP messages.
     * It is the SOAP equivalent of RestTemplate for REST.
     *
     * We inject the marshaller so it knows how to convert
     * Java objects to XML and back automatically.
     */
    @Bean
    public WebServiceTemplate webServiceTemplate(Jaxb2Marshaller jaxb2Marshaller) {
        WebServiceTemplate template = new WebServiceTemplate();

        template.setMarshaller(jaxb2Marshaller);
        template.setUnmarshaller(jaxb2Marshaller);
        template.setDefaultUri(afipSoapUrl);

        return template;
    }
}