package com.xmut.ebus.application.business.agent.workspace;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * {@code ebus.workspace.*} — per-run agent workspace root on disk.
 */
@ConfigurationProperties(prefix = "ebus.workspace")
public class RunWorkspaceProperties {

    /** Optional raw config; bind via {@code EBUS_WORKSPACE_ROOT} or {@code ebus.workspace.root}. */
    private String root;

    public void setRoot(String root) {
        this.root = root;
    }

    /**
     * Resolved workspace root: configured path or {@code ~/.ebus}.
     */
    public Path getRoot() {
        if (root == null || root.trim().isEmpty()) {
            return Paths.get(System.getProperty("user.home"), ".ebus");
        }
        return Paths.get(root.trim());
    }
}
