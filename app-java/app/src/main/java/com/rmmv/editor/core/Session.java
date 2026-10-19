package com.rmmv.editor.core;

import android.content.Context;

/**
 * Process-wide holder for the open project, mirroring the PC editor's
 * "Main Window" session: one project open at a time, shared by the Database,
 * Plugin Manager, Resource Manager and Map Editor screens.
 */
public final class Session {

    private static ProjectStore store;
    private static Project project;

    private Session() { }

    public static synchronized ProjectStore store(Context ctx) {
        if (store == null) store = new ProjectStore(ctx.getApplicationContext());
        return store;
    }

    public static synchronized Project project() {
        return project;
    }

    public static synchronized void open(Context ctx, String projectName) throws java.io.IOException {
        ProjectStore s = store(ctx);
        if (project != null) project.close();
        project = new Project(s);
        project.open(projectName);
    }

    /** Reuse the open project if it matches, else open it. */
    public static synchronized Project require(Context ctx, String projectName) throws java.io.IOException {
        if (project == null || !projectName.equals(project.name())) {
            open(ctx, projectName);
        }
        return project;
    }

    public static synchronized void close() {
        if (project != null) project.close();
        project = null;
    }
}
