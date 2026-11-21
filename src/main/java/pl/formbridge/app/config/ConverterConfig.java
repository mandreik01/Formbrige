package pl.formbridge.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.formbridge.core.FormBridgeConverter;
import pl.formbridge.core.ai.LlamaCppClient;

import java.util.ArrayList;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class ConverterConfig {

    @Bean
    FormBridgeConverter formBridgeConverter(FormBridgeProperties properties) {
        FormBridgeProperties.Llm llm = properties.llm();
        List<LlamaCppClient.Endpoint> endpoints = parseEndpoints(llm.endpoints());
        if (!llm.enabled() || endpoints.isEmpty()) {
            return new FormBridgeConverter();
        }
        return FormBridgeConverter.withLocalLlm(endpoints, llm.timeout());
    }

    static List<LlamaCppClient.Endpoint> parseEndpoints(List<String> raw) {
        List<LlamaCppClient.Endpoint> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int eq = entry.lastIndexOf('=');
            String url = (eq < 0 ? entry : entry.substring(0, eq)).trim();
            String model = eq < 0 ? "local" : entry.substring(eq + 1).trim();
            if (!url.isEmpty()) {
                result.add(new LlamaCppClient.Endpoint(url, model.isEmpty() ? "local" : model));
            }
        }
        return result;
    }
}
