package com.xmut.forma.pi.agent.extension;

import com.xmut.forma.pi.agent.event.PiEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 扩展装配器。
 * 功能描述：发现并注册 PiExtension，要求恰好一个 ToolPolicyExtension 且最先挂载。
 */
public final class ExtensionRunner implements PiExtensionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ExtensionRunner.class);

    private final List<PiExtension> extensions;

    public ExtensionRunner(List<PiExtension> extensions) {
        this.extensions = orderPolicyFirst(mergeDiscoverable(extensions));
    }

    @Override
    public void register(PiEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus");
        }
        for (PiExtension ext : extensions) {
            ext.register(bus);
        }
    }

    public List<String> extensionNames() {
        List<String> names = new ArrayList<>();
        for (PiExtension ext : extensions) {
            names.add(ext.getClass().getSimpleName());
        }
        return Collections.unmodifiableList(names);
    }

    private static List<PiExtension> orderPolicyFirst(List<PiExtension> all) {
        ToolPolicyExtension policy = null;
        List<PiExtension> rest = new ArrayList<>();
        for (PiExtension ext : all) {
            if (ext instanceof ToolPolicyExtension) {
                if (policy != null) {
                    throw new IllegalArgumentException("exactly one ToolPolicyExtension is required");
                }
                policy = (ToolPolicyExtension) ext;
            } else {
                rest.add(ext);
            }
        }
        if (policy == null) {
            throw new IllegalArgumentException("ToolPolicyExtension is required");
        }
        List<PiExtension> ordered = new ArrayList<>();
        ordered.add(policy);
        ordered.addAll(rest);
        return Collections.unmodifiableList(ordered);
    }

    private static List<PiExtension> mergeDiscoverable(List<PiExtension> springBeans) {
        LinkedHashSet<PiExtension> out = new LinkedHashSet<>();
        out.addAll(springBeans);
        try {
            for (PiExtension ext : ServiceLoader.load(PiExtension.class)) {
                out.add(ext);
            }
        } catch (RuntimeException | java.util.ServiceConfigurationError ex) {
            log.warn("PiExtension SPI load failed: {}", ex.toString());
        }
        return new ArrayList<>(out);
    }
}
