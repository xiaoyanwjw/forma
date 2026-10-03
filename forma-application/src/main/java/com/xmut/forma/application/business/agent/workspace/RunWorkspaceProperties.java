package com.xmut.forma.application.business.agent.workspace;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * {@code forma.workspace.*} — per-run agent workspace root on disk.
 */
@ConfigurationProperties(prefix = "forma.workspace")
public class RunWorkspaceProperties {

    /** Optional raw config; bind via {@code FORMA_WORKSPACE_ROOT} or {@code forma.workspace.root}. */
    private String root;

    public void setRoot(String root) {
        this.root = root;
    }

    /**
     * Resolved workspace root: configured path or {@code ~/.forma}.
     */
    public Path getRoot() {
        if (root == null || root.trim().isEmpty()) {
            return Paths.get(System.getProperty("user.home"), ".forma");
        }
        return Paths.get(root.trim());
    }
}
