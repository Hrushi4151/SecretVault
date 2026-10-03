package io.secretvault.starter.annotation;

import io.secretvault.sdk.api.SecretVaultClient;
import io.secretvault.sdk.model.SecretValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/**
 * Injects secret values into bean fields annotated with {@link SecretVaultValue} and registers rotation listeners.
 */
public class SecretVaultValueBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SecretVaultValueBeanPostProcessor.class);

    private final SecretVaultClient client;

    public SecretVaultValueBeanPostProcessor(SecretVaultClient client) {
        this.client = client;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();

        ReflectionUtils.doWithFields(targetClass, field -> {
            SecretVaultValue annotation = field.getAnnotation(SecretVaultValue.class);
            if (annotation != null) {
                String secretKey = annotation.value();
                try {
                    SecretValue secretValue = client.secrets().get(secretKey);
                    if (secretValue != null) {
                        ReflectionUtils.makeAccessible(field);
                        setFieldValue(field, bean, secretValue.value());

                        if (annotation.autoRefresh()) {
                            client.secrets().watch(secretKey, (event, newValue) -> {
                                if (newValue != null) {
                                    log.info("Applying dynamic secret update for field '{}' in bean '{}' (version {})",
                                            field.getName(), beanName, newValue.version());
                                    ReflectionUtils.makeAccessible(field);
                                    setFieldValue(field, bean, newValue.value());
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to inject @SecretVaultValue('{}') into field '{}' of bean '{}': {}",
                            secretKey, field.getName(), beanName, e.getMessage());
                }
            }
        });

        return bean;
    }

    private void setFieldValue(Field field, Object target, String val) {
        try {
            if (field.getType().equals(String.class)) {
                field.set(target, val);
            } else if (field.getType().equals(char[].class)) {
                field.set(target, val.toCharArray());
            } else {
                field.set(target, val);
            }
        } catch (IllegalAccessException e) {
            log.error("Unable to set field '{}': {}", field.getName(), e.getMessage());
        }
    }
}
