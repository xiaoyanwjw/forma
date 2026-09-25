package com.xmut.lims.pi.agent.tool;

import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ToolHandler 自动装配器。
 * 功能描述：按 Manifest.handlerClass 解析或创建 Handler Bean。
 */
public final class ToolHandlerAutoBinder {

    private static final Logger log = LoggerFactory.getLogger(ToolHandlerAutoBinder.class);

    private ToolHandlerAutoBinder() {}

    /**
     * @return 仅含声明了 {@code handlerClass} 的 Binding（handlerOnly 占位 Manifest）
     */
    public static List<ToolBinding> bindFromManifests(List<ToolManifest> manifests,
                                                      BeanFactory beanFactory) {
        if (manifests == null || manifests.isEmpty() || beanFactory == null) {
            return Collections.emptyList();
        }
        AutowireCapableBeanFactory autowire = null;
        if (beanFactory instanceof AutowireCapableBeanFactory) {
            autowire = (AutowireCapableBeanFactory) beanFactory;
        }
        ListableBeanFactory listable = beanFactory instanceof ListableBeanFactory
                ? (ListableBeanFactory) beanFactory
                : null;

        List<ToolBinding> out = new ArrayList<>();
        for (ToolManifest m : manifests) {
            if (m == null || !StringUtils.hasText(m.getHandlerClass())) {
                continue;
            }
            ToolHandler handler = resolve(m.getHandlerClass().trim(), listable, autowire);
            out.add(ToolBinding.handlerOnly(m.getId(), handler));
            log.info("ToolHandlerAutoBinder: bound id={} handlerClass={}",
                    m.getId(), m.getHandlerClass());
        }
        return Collections.unmodifiableList(out);
    }

    static ToolHandler resolve(String handlerClass,
                               ListableBeanFactory listable,
                               AutowireCapableBeanFactory autowire) {
        Class<?> clazz;
        try {
            clazz = ClassUtils.forName(handlerClass, ToolHandlerAutoBinder.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new ToolValidationException(
                    "tool handlerClass not found: " + handlerClass, e);
        }
        if (!ToolHandler.class.isAssignableFrom(clazz)) {
            throw new ToolValidationException(
                    "tool handlerClass must implement ToolHandler: " + handlerClass);
        }

        Object bean = null;
        if (listable != null) {
            String[] names = listable.getBeanNamesForType(clazz, true, false);
            if (names != null && names.length > 0) {
                bean = listable.getBean(names[0]);
            }
        }
        if (bean == null) {
            if (autowire == null) {
                throw new ToolValidationException(
                        "cannot create handlerClass (no AutowireCapableBeanFactory): "
                                + handlerClass);
            }
            bean = autowire.createBean(clazz);
        }
        return (ToolHandler) bean;
    }
}
